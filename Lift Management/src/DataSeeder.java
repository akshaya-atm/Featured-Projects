import com.akshaya.liftmanagement.Lift;
import com.akshaya.liftmanagement.LiftRepo;

public class DataSeeder {
    
    public static void addNewLift(int n){
        LiftRepo liftRepo = LiftRepo.getInstance();
        for(int i =0; i < n;i++)
        liftRepo.addLift(new Lift());
    }


}
