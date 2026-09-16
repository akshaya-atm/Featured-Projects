package com.akshaya.outlast.agent;

import com.akshaya.outlast.client.LLMClient;
import com.akshaya.outlast.config.*;
import com.akshaya.outlast.context.*;
import com.akshaya.outlast.model.*;
import com.akshaya.outlast.prompt.HostSystemPromptBuilder;
import com.akshaya.outlast.utils.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class GameRulesRegressionTests {
    private GameConfig config() {
        GameConfig config = new GameConfig() {
            @Override public Host host() { return new Host("Host", "Fair", "Fair", "test"); }
        };
        config.setLimits(new GameConfig.Limits(20,30,2,2,4,2,20,10,4,35,45,25));
        return config;
    }
    private World world() {
        return new World("Test", "Island", "Survive", new World.Environment("Tropical", "Rain", List.of(), List.of()), List.of(), List.of(), List.of(), Map.of(), "Win", null, null, null);
    }
    private Player player(String id, int grit, int cooperation) {
        Player p = new Player(id,"Player " + id,"test", Gender.FEMALE, PersonalityType.CALM_STRATEGIST);
        p.setSystemPrompt(id);
        p.setStat(StatType.SURVIVAL_GRIT, grit);
        p.setStat(StatType.COOPERATION, cooperation);
        p.setStat(StatType.TRUSTWORTHINESS,100);
        return p;
    }
    private Team team(String name, Player... players) {
        Team t = new Team(name); for(Player p:players)t.addPlayer(p);t.setCaptain(players[0]);return t;
    }
    private Challenge challenge() { return new Challenge(ChallengeTheme.SURVIVAL,"Fire","Make fire","Light it", List.of(),List.of(),List.of()); }
    private HostAgent host(LLMClient client) {
        GameConfig c=config();World w=world();return new HostAgent(client,new HostSystemPromptBuilder(new WorldContext(),w,c),c,w);
    }

    @Test void silencePreservesTargetsAndReason() {
        Player a=player("1",50,50),b=player("2",50,50),c=player("3",50,50);
        List<Player> players=List.of(a,b,c);players.forEach(p->p.initializeRelations(players));
        GameContext ctx=new GameContext();ctx.setActivePlayers(players);
        LLMClient llm=(model,messages)->"""
            {"to_id":null,"trueTarget":"2","backupTarget":"3","trueReasoning":"Stay quiet and vote strategically"}
            """;
        CampLifePhase camp=new CampLifePhase(llm,config(),world(),ctx);
        camp.runSecretMessages(players,"Camp");
        assertEquals("2",a.getTrueTarget());assertEquals("3",a.getBackupTarget());
        assertEquals("Stay quiet and vote strategically",a.getTrueReasoning());assertTrue(a.getMessageLog().isEmpty());
    }

    @Test void strategyDiscussionUsesOneCallPerPlayer() {
        Player a=player("1",50,50),b=player("2",50,50),c=player("3",50,50);
        List<Player> players=List.of(a,b,c);players.forEach(p->p.initializeRelations(players));
        GameContext ctx=new GameContext();ctx.setActivePlayers(players);
        AtomicInteger totalCalls=new AtomicInteger();
        LLMClient llm=(model,messages)->{
            String id=messages.get(0).content(),prompt=messages.get(1).content();
            totalCalls.incrementAndGet();
            if(id.equals("2"))return "{\"to_id\":\"1\",\"trueTarget\":\"3\",\"honestMessage\":\"New information: choose player 3.\",\"deceptiveMessage\":\"New information: choose player 3.\"}";
            return "{\"to_id\":null,\"trueTarget\":\"2\",\"trueReasoning\":\"Earlier plan\"}";
        };
        CampLifePhase camp=new CampLifePhase(llm,config(),world(),ctx);
        TribalCouncilPhase council=new TribalCouncilPhase(llm,config(),ctx,camp);
        Team t=team("Test",a,b,c);council.runStrategyConversations(t,null,"Tonight");
        assertEquals(3,totalCalls.get());assertEquals("2",a.getTrueTarget());
        assertTrue(a.getMessageLogSummary().contains("New information: choose player 3."));
        assertSame(b,council.castVotes(t).get(0).target());
    }

    @Test void cachedDecisionCannotVoteForImmunePlayer() {
        Player a=player("1",50,50),b=player("2",50,50),c=player("3",50,50);
        List<Player> players=List.of(a,b,c);a.setTrueTarget("2");
        GameContext ctx=new GameContext();ctx.setActivePlayers(players);
        LLMClient llm=(model,messages)->"{\"trueTarget\":\"2\"}";
        CampLifePhase camp=new CampLifePhase(llm,config(),world(),ctx);
        TribalCouncilPhase council=new TribalCouncilPhase(llm,config(),ctx,camp);
        List<Vote> votes=council.castMergeVotes(players,b,"NORMAL",c);
        assertTrue(votes.stream().allMatch(v->v.target()!=b && v.target()!=v.voter()));
    }

    @Test void sameWinnerAndLoserUsesAllActiveTeamsForFallback() {
        Team a=team("A",player("1",50,50)),b=team("B",player("2",50,90)),c=team("C",player("3",50,10)),empty=new Team("Empty");
        HostAgent host=host((model,messages)->"{\"winningTeamName\":\"A\",\"losingTeamName\":\"A\",\"reasoning\":\"Oops\"}");
        Map<String,UpgradedIdea> plans=new HashMap<>();for(Team t:List.of(a,b,c))plans.put(t.getTeamName(),new UpgradedIdea(t.getCaptain(),"Plan"));
        ChallengeResult result=host.judgeChallenge(List.of(a,b,c,empty),plans,challenge());
        assertEquals("B",result.winningTeamName());assertEquals("C",result.losingTeamName());
    }

    @Test void providerFailureUsesChallengeFallback() {
        Team a=team("A",player("1",50,10)),b=team("B",player("2",90,90));
        HostAgent host=host((model,messages)->{throw new RuntimeException("offline");});
        ChallengeResult result=host.judgeChallenge(List.of(a,b),Map.of("A",new UpgradedIdea(a.getCaptain(),"Plan"),"B",new UpgradedIdea(b.getCaptain(),"Plan")),challenge());
        assertEquals("B",result.winningTeamName());
        MergeChallengeResult ranked=host.rankIndividualChallenge(List.of(a.getCaptain(),b.getCaptain()),Map.of("1","Plan","2","Plan"),challenge());
        assertSame(b.getCaptain(),ranked.immunityWinner());
    }

    @Test void duplicateRanksFallBackToGrit() {
        Player a=player("1",10,50),b=player("2",90,50);
        HostAgent host=host((model,messages)->"[{\"playerId\":\"1\",\"rank\":1},{\"playerId\":\"2\",\"rank\":1}]");
        MergeChallengeResult result=host.rankIndividualChallenge(List.of(a,b),Map.of("1","Plan","2","Plan"),challenge());
        assertSame(b,result.immunityWinner());assertEquals(1,b.getIndividualWins());
    }

    @Test void tiedTeamVoteUsesGritInsteadOfHashOrder() {
        Player a=player("1",90,50),b=player("2",10,50);GameContext ctx=new GameContext();ctx.setActivePlayers(List.of(a,b));
        TribalCouncilPhase council=new TribalCouncilPhase(null,config(),ctx,null);
        assertEquals(List.of(b),council.processVotes(team("Test",a,b),List.of(new Vote(a,b,""),new Vote(b,a,"")),"NORMAL"));
    }

    @Test void loneLosingPlayerDoesNotStall() {
        Player a=player("1",90,50);GameContext ctx=new GameContext();ctx.setActivePlayers(List.of(a));
        TribalCouncilPhase council=new TribalCouncilPhase(null,config(),ctx,null);
        assertEquals(List.of(a),council.processVotes(team("Test",a),List.of(new Vote(a,null,"")),"NORMAL"));
    }

    @Test void failedJuryCallUsesJurorTrustInsteadOfFirstFinalist() {
        Player a=player("1",50,50),b=player("2",50,50),juror=player("3",50,50);
        juror.initializeRelations(List.of(a,b));juror.setTrustScore("1",10);juror.setTrustScore("2",90);
        TribalCouncilPhase council=new TribalCouncilPhase((model,messages)->{throw new RuntimeException("offline");},config(),new GameContext(),null);
        assertSame(b,council.runJuryVote(List.of(juror),a,b,a));
    }
}
