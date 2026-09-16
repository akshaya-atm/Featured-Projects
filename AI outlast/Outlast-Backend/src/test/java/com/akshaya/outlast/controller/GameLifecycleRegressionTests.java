package com.akshaya.outlast.controller;

import com.akshaya.outlast.GameSimulationRunner;
import com.akshaya.outlast.context.GameContext;
import com.akshaya.outlast.model.GameConfigOptions;
import com.akshaya.outlast.utils.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class GameLifecycleRegressionTests {
    private GameSimulationRunner runner(Runnable run) {
        return new GameSimulationRunner(null,null,null,null,null,null,null,null) {
            @Override public void runGame(GameConfigOptions options) { run.run(); }
        };
    }
    @Test void resetStopsPausedWorkerAndPermitsNewStart() throws Exception {
        GameContext ctx=new GameContext();GameLogService logs=new GameLogService();
        CountDownLatch entered=new CountDownLatch(1),exited=new CountDownLatch(1);
        GameSimulationRunner runner=runner(()->{try{entered.countDown();ctx.checkpoint("Camp");}catch(RuntimeException e){/* cancellation */}finally{exited.countDown();}});
        GameController controller=new GameController(ctx,runner,logs);
        GameConfigOptions settings=new GameConfigOptions();settings.setStepByStep(true);
        try {
            assertTrue(controller.startGame(settings).getStatusCode().is2xxSuccessful());
            assertTrue(entered.await(1,TimeUnit.SECONDS));
            assertTrue(controller.resetGame().getStatusCode().is2xxSuccessful());
            assertTrue(exited.await(1,TimeUnit.SECONDS));
            assertEquals("NOT_STARTED",ctx.getStatus());assertFalse(ctx.isPaused());
            assertTrue(controller.startGame(settings).getStatusCode().is2xxSuccessful());
        } finally {controller.resetGame();logs.stopCapturing();}
    }
    @Test void busyProviderCannotWriteIntoANewGame() throws Exception {
        GameContext ctx=new GameContext();GameLogService logs=new GameLogService();
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),exited=new CountDownLatch(1);
        GameController controller=new GameController(ctx,runner(()->{
            entered.countDown();
            boolean waiting=true;
            while(waiting){try{release.await();waiting=false;}catch(InterruptedException ignored){/* Models a call that does not stop promptly. */}}
            exited.countDown();
        }),logs);
        try {
            assertTrue(controller.startGame(new GameConfigOptions()).getStatusCode().is2xxSuccessful());
            assertTrue(entered.await(1,TimeUnit.SECONDS));
            assertEquals(409,controller.resetGame().getStatusCode().value());
            assertEquals("STOPPING",ctx.getStatus());
            assertEquals(400,controller.startGame(new GameConfigOptions()).getStatusCode().value());
            release.countDown();assertTrue(exited.await(1,TimeUnit.SECONDS));
            assertTrue(controller.resetGame().getStatusCode().is2xxSuccessful());
            assertEquals("NOT_STARTED",ctx.getStatus());
        } finally {release.countDown();controller.resetGame();logs.stopCapturing();}
    }
    @Test void invalidSetupIsRejectedBeforeClearingState() {
        GameContext ctx=new GameContext();ctx.setWinnerName("Previous winner");
        GameController controller=new GameController(ctx,runner(()->fail("Must not start")),new GameLogService());
        GameConfigOptions bad=new GameConfigOptions();bad.setNumberOfTeams(4);bad.setNumberOfPlayers(4);
        assertEquals(400,controller.startGame(bad).getStatusCode().value());
        assertEquals("Previous winner",ctx.getWinnerName());
    }
    @Test void customCastMustHaveCorrectCountAndUniqueNames() {
        GameConfigOptions config=new GameConfigOptions();config.setUseDefaults(false);
        config.setNumberOfPlayers(4);config.setCustomPlayers(List.of());
        assertThrows(IllegalArgumentException.class,config::validate);
        List<GameConfigOptions.PlayerDetails> players=new ArrayList<>();
        for(String name:List.of("Mara"," mara ","Deon","Aria")){
            GameConfigOptions.PlayerDetails p=new GameConfigOptions.PlayerDetails();p.setName(name);p.setGender(Gender.FEMALE);players.add(p);
        }
        config.setCustomPlayers(players);assertThrows(IllegalArgumentException.class,config::validate);
        players.get(1).setName("Chloe");assertDoesNotThrow(config::validate);
    }
}
