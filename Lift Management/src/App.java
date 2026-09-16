import com.akshaya.liftmanagement.LiftController;
import com.akshaya.liftmanagement.LiftService;
import com.akshaya.liftmanagement.LiftRepo;
import com.akshaya.liftmanagement.LiftView;

public class App {
    public static void main(String[] args) throws Exception {

        DataSeeder.addNewLift(3);
        LiftView liftView  =new LiftView();
        LiftRepo repo = LiftRepo.getInstance();
        LiftService liftService = new LiftService(repo);
        LiftController controller = new LiftController(liftView
            , liftService);
            controller.start();

    }
}
