package com.akshaya.outlast;

import com.akshaya.outlast.agent.*;
import com.akshaya.outlast.context.GameContext;
import com.akshaya.outlast.model.*;
import com.akshaya.outlast.utils.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationFlowRegressionTests {
    @Test void largerCastCompletesInStepModeWithNoLiveLlm() throws Exception { runSeason(12,true); }
    @Test void defaultCastCompletesInContinuousModeWithNoLiveLlm() throws Exception { runSeason(6,false); }

    private void runSeason(int count, boolean step) throws Exception {
        GameContext ctx=new GameContext();ctx.setWebMode(true);ctx.setStepByStep(step);
        GameInitializer init=new GameInitializer(null,null,ctx) {
            @Override public List<Player> initializePlayers(List<PlayerInitRequest> requests) {
                List<Player> players=new ArrayList<>();
                for(int i=0;i<requests.size();i++) {
                    Player p=new Player(""+(i+1),"Player "+(i+1),"fake",Gender.FEMALE,PersonalityType.CALM_STRATEGIST);
                    p.setIntroduction("Ready");p.setStat(StatType.SURVIVAL_GRIT,i+1);players.add(p);
                }
                ctx.setActivePlayers(players);return players;
            }
        };
        SetUpTeams draft=new SetUpTeams(null,ctx,null,null,null) {
            @Override public List<Team> assignCaptains(int n) {
                List<Team> teams=new ArrayList<>();for(int i=0;i<n;i++){Team t=new Team("Tribe"+i);teams.add(t);}
                int i=0;for(Player p:ctx.getActivePlayers())teams.get(i++%n).addPlayer(p);
                teams.forEach(t->t.setCaptain(t.getPlayers().get(0)));return teams;
            }
            @Override public List<Team> runDraft(List<Team> teams){ctx.setTeams(teams);return teams;}
        };
        HostAgent host=new HostAgent(null,null,null,null) {
            @Override public String welcomeContestants(List<Player> players){return "Welcome";}
            @Override public String announceCaptains(List<Player> captains,List<Team> teams){return "Captains";}
            @Override public String announceTeams(List<Team> teams){return "Teams";}
            @Override public Challenge createChallenge(boolean merge){return new Challenge(ChallengeTheme.SURVIVAL,"Test","Test","Win",List.of(),List.of(),List.of());}
            @Override public ChallengeResult judgeChallenge(List<Team> teams,Map<String,UpgradedIdea> plans,Challenge challenge){return new ChallengeResult(teams.get(0).getTeamName(),teams.get(1).getTeamName(),"Test");}
            @Override public MergeChallengeResult rankIndividualChallenge(List<Player> players,Map<String,String> plans,Challenge challenge){
                List<PlayerRanking> rankings=new ArrayList<>();for(int i=0;i<players.size();i++)rankings.add(new PlayerRanking(players.get(i),i+1,"Test"));
                return new MergeChallengeResult(rankings,players.get(0),players.get(players.size()-1));
            }
        };
        CampLifePhase camp=new CampLifePhase(null,null,null,ctx) {
            @Override public List<SecretMessage> runSecretMessages(List<Player> players,String context){return List.of();}
        };
        ChallengePhase challenge=new ChallengePhase(null,null) {
            @Override public TeamChallengeResult runTeamChallengeFlow(Team team,Challenge c){return new TeamChallengeResult(new UpgradedIdea(team.getCaptain(),"Plan"),List.of(),List.of(),Map.of());}
            @Override public Map<String,String> runIndividualChallenge(List<Player> players,Challenge c){return Map.of();}
        };
        TribalCouncilPhase council=new TribalCouncilPhase(null,null,ctx,camp) {
            @Override public List<SecretMessage> runStrategyConversations(Team team,Player immune,String context){
                for(Player p:team.getPlayers())p.setTrueTarget(team.getPlayers().stream().filter(t->t!=p && t!=immune).findFirst().map(Player::getId).orElse(null));
                return List.of();
            }
            @Override public Player runJuryVote(List<Player> jury,Player a,Player b,Player tie){return a;}
        };
        GameSimulationRunner runner=new GameSimulationRunner(init,draft,host,camp,challenge,council,ctx,null);
        GameConfigOptions options=new GameConfigOptions();options.setNumberOfPlayers(count);options.setStepByStep(step);
        Thread worker=new Thread(()->runner.runGame(options));worker.setDaemon(true);worker.start();
        long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while(worker.isAlive()&&System.nanoTime()<until){
            // Never resume a finished game: a terminal checkpoint would reproduce the old bug.
            if(ctx.isPaused() && !"Game Over - Winner Crowned".equals(ctx.getCurrentPhase()))ctx.resume();
            worker.join(2);
        }
        try {
            assertFalse(worker.isAlive(),"Season stalled");
            assertEquals("COMPLETED",ctx.getStatus());assertFalse(ctx.isPaused());
            assertEquals(1,ctx.getActivePlayers().size());assertNotNull(ctx.getWinnerName());
        } finally {worker.interrupt();worker.join(1000);}
    }
}
