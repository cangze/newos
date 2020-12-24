package nachos.threads;

import nachos.ag.BoatGrader;
import nachos.machine.Lib;

public class Boat {
	static BoatGrader bg;
	/** 父进程 */
	private static KThread parentThread;
	/** 在Oahu岛上孩子的数量 */
	private static int children_inOahu;
	/** 在Oahu岛上成人的数量 */
	private static int adult_inOahu;
	/** 在Moloka岛上孩子的数量 */
	private static int children_inMolokai;
	/** 在Moloka岛上成人的数量 */
	private static int adult_inMolokai;
	/** 孩子在Oahu岛上的条件变量 */
	private static Condition children_condition_Oahu;
	/** 孩子在Molokai岛上的条件变量 */
	private static Condition children_condition_Molokai;
	/** 成人在Oahu岛上的条件变量 */
	private static Condition adult_condition_Oahu;

	private static Lock lock;
	/** 判断是否该成人走 */
	private static boolean adult_can_go;
	/** 判断船是否在Oahu */
	private static boolean boat_Oahu;
	/** 判断目前的孩子是否是驾驶员 */
	private static boolean isPilot;
	/** 判断是否结束 */
	private static boolean isFinish;
	static boolean is_first_go= true;
	static boolean newOne=true;
	static int ChildrenN;
	public static void selfTest() {
		BoatGrader b = new BoatGrader();

//		System.out.println("\n ***Testing Boats with only 2 children***");
//		begin(0, 2, b);

//		 System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
//		 begin(3, 3, b);

		 System.out.println("\n ***Testing Boats with 3 children, 4 adults***");
		 begin(3, 4, b);
	}
	

	public static void begin(int adults, int children, BoatGrader b) {
		// Store the externally generated autograder in a class
		// variable to be accessible by children.
		Lib.assertTrue(children>=2);
		Lib.assertTrue(b!=null);
		ChildrenN=children;
		bg = b;
		//把当前的线程声明为父线程
		parentThread = KThread.currentThread();
		// Create threads here. See section 3.4 of the Nachos for Java
		// Walkthrough linked from the projects page.
		//创建adults个大人线程
		for (int i = 0; i < adults; i++){
			Runnable r = new Runnable() {
				public void run() {
					AdultItinerary();
				}
			};
			KThread t = new KThread(r);
			t.setName("Adult"+i+"Thread").fork();
		}
		//创建children个小孩线程
		for (int i = 0; i < children; i++){
			Runnable r = new Runnable() {
				public void run() {
					if(ChildrenN>1){
						newOne=true;
						ChildrenN--;
					}
					else{
						newOne=false;
					}
					ChildItinerary();
				}
			};
			KThread t = new KThread(r);
			t.setName("Children"+i+"Thread").fork();
		}
		
		// Instantiate global variables here
		children_inOahu = children;
		adult_inOahu = adults;
		children_inMolokai = 0;
		adult_inMolokai = 0;
		lock = new Lock();
		children_condition_Oahu = new Condition(lock);
		children_condition_Molokai = new Condition(lock);
		adult_condition_Oahu = new Condition(lock);
		isPilot = true;
		adult_can_go = false;
		isFinish = false;
		boat_Oahu = true;
/*
		Runnable r = new Runnable() {
			public void run() {
				SampleItinerary();
			}
		};
		KThread t = new KThread(r);
		t.setName("Sample Boat Thread");
		t.fork();
*/
	}

	static void AdultItinerary() {
		bg.initializeAdult(); // Required for autograder interface. Must be the
								// first thing called.
		// DO NOT PUT ANYTHING ABOVE THIS LINE.
		lock.acquire();
		//如果成年人不能走,则将条件变量睡眠
		if(!(adult_can_go&&boat_Oahu))
		{
			adult_condition_Oahu.sleep();
		}
		bg.AdultRowToMolokai();	//adult to Molokai
		adult_inOahu--;			//在Oahu的成人数量-1
		adult_inMolokai++;		//在Molokai的成人数量+1
		adult_can_go = false;	//这一次是成人走，下一次必须是孩子走。因为要保证Molokai要至少有一个孩子
		boat_Oahu = false;		//成人过去了，船也过去了。
		
		children_condition_Molokai.wake();//唤醒一个在Molokai的孩子，将船驶回Oahu
		lock.release();
		/*
		 * This is where you should put your solutions. Make calls to the
		 * BoatGrader to show that it is synchronized. For example:
		 * bg.AdultRowToMolokai(); indicates that an adult has rowed the boat
		 * across to Molokai
		 */
	}

	static void ChildItinerary() {
		bg.initializeChild(); // Required for autograder interface. Must be the
								// first thing called.
		// DO NOT PUT ANYTHING ABOVE THIS LINE.
		boolean is_on_Oahu = true;

		lock.acquire();
			while(!isFinish){
				//运输没有完成
				if(is_on_Oahu){
					//如果这个孩子在Oahu上
					if(!boat_Oahu||adult_can_go||newOne){
						//如果船没在Oahu、或者该成人走了，该孩子睡眠
						children_condition_Oahu.sleep();
					}
					if(isPilot){
						//如果该孩子是船长
						bg.ChildRowToMolokai();
						is_on_Oahu=false;
						children_inOahu--;				//Oahu的孩子减少一个
						children_inMolokai++;			//Molokai的孩子增加一个
						isPilot = false;					
						//他是船长，再呼唤一个 孩子一起走
						children_condition_Oahu.wake();
//						boat_Oahu=false;			//把船设为不在Oahu
						children_condition_Molokai.sleep();	//把孩子设为molokai并且不能走
					}
					else{
						if(adult_inOahu==0&&children_inOahu==1)
							isFinish=true;//运输即将完成
						//如果孩子不是船长，且在Oahu的成人不为0，那么该孩子就作为乘客走
						if(adult_inOahu!=0)
							adult_can_go=true;
						bg.ChildRideToMolokai();
						is_on_Oahu=false;
						boat_Oahu=false;
						children_inOahu--;
						children_inMolokai++;
						isPilot=true;	//把船开过去后，孩子在Molokai岛都可以作舵手
						if(!isFinish){
							//Molokai岛上唤醒一个孩子
							children_condition_Molokai.wake();
						}
						//Molokai岛上的当前孩子休眠
						children_condition_Molokai.sleep();
					}
				}
				
				
				else{
					//如果不在Oahu,则孩子划船去Oahu
					bg.ChildRowToOahu();
					is_on_Oahu=true;
					boat_Oahu=true;
					children_inMolokai--;
					children_inOahu++;
					
					if(adult_inOahu==0){
						//如果 在Oahu的成人数量为0,则成人不可走,把孩子唤醒
						adult_can_go=false;
						children_condition_Oahu.wake();
					}else{
						if(adult_can_go)
							adult_condition_Oahu.wake();
						else
							children_condition_Oahu.wake();
					}
					children_condition_Oahu.sleep();
				}
			}
		lock.release();
	}

	static void SampleItinerary() {
		// Please note that this isn't a valid solution (you can't fit
		// all of them on the boat). Please also note that you may not
		// have a single thread calculate a solution and then just play
		// it back at the autograder -- you will be caught.
		System.out.println("\n ***Everyone piles on the boat and goes to Molokai***");
		bg.AdultRowToMolokai();
		bg.ChildRideToMolokai();
		bg.AdultRideToMolokai();
		bg.ChildRideToMolokai();
	}

}
