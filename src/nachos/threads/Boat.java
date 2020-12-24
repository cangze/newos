package nachos.threads;

import nachos.ag.BoatGrader;
import nachos.machine.Lib;

public class Boat {
	static BoatGrader bg;
	/** ������ */
	private static KThread parentThread;
	/** ��Oahu���Ϻ��ӵ����� */
	private static int children_inOahu;
	/** ��Oahu���ϳ��˵����� */
	private static int adult_inOahu;
	/** ��Moloka���Ϻ��ӵ����� */
	private static int children_inMolokai;
	/** ��Moloka���ϳ��˵����� */
	private static int adult_inMolokai;
	/** ������Oahu���ϵ��������� */
	private static Condition children_condition_Oahu;
	/** ������Molokai���ϵ��������� */
	private static Condition children_condition_Molokai;
	/** ������Oahu���ϵ��������� */
	private static Condition adult_condition_Oahu;

	private static Lock lock;
	/** �ж��Ƿ�ó����� */
	private static boolean adult_can_go;
	/** �жϴ��Ƿ���Oahu */
	private static boolean boat_Oahu;
	/** �ж�Ŀǰ�ĺ����Ƿ��Ǽ�ʻԱ */
	private static boolean isPilot;
	/** �ж��Ƿ���� */
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
		//�ѵ�ǰ���߳�����Ϊ���߳�
		parentThread = KThread.currentThread();
		// Create threads here. See section 3.4 of the Nachos for Java
		// Walkthrough linked from the projects page.
		//����adults�������߳�
		for (int i = 0; i < adults; i++){
			Runnable r = new Runnable() {
				public void run() {
					AdultItinerary();
				}
			};
			KThread t = new KThread(r);
			t.setName("Adult"+i+"Thread").fork();
		}
		//����children��С���߳�
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
		//��������˲�����,����������˯��
		if(!(adult_can_go&&boat_Oahu))
		{
			adult_condition_Oahu.sleep();
		}
		bg.AdultRowToMolokai();	//adult to Molokai
		adult_inOahu--;			//��Oahu�ĳ�������-1
		adult_inMolokai++;		//��Molokai�ĳ�������+1
		adult_can_go = false;	//��һ���ǳ����ߣ���һ�α����Ǻ����ߡ���ΪҪ��֤MolokaiҪ������һ������
		boat_Oahu = false;		//���˹�ȥ�ˣ���Ҳ��ȥ�ˡ�
		
		children_condition_Molokai.wake();//����һ����Molokai�ĺ��ӣ�����ʻ��Oahu
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
				//����û�����
				if(is_on_Oahu){
					//������������Oahu��
					if(!boat_Oahu||adult_can_go||newOne){
						//�����û��Oahu�����߸ó������ˣ��ú���˯��
						children_condition_Oahu.sleep();
					}
					if(isPilot){
						//����ú����Ǵ���
						bg.ChildRowToMolokai();
						is_on_Oahu=false;
						children_inOahu--;				//Oahu�ĺ��Ӽ���һ��
						children_inMolokai++;			//Molokai�ĺ�������һ��
						isPilot = false;					
						//���Ǵ������ٺ���һ�� ����һ����
						children_condition_Oahu.wake();
//						boat_Oahu=false;			//�Ѵ���Ϊ����Oahu
						children_condition_Molokai.sleep();	//�Ѻ�����Ϊmolokai���Ҳ�����
					}
					else{
						if(adult_inOahu==0&&children_inOahu==1)
							isFinish=true;//���伴�����
						//������Ӳ��Ǵ���������Oahu�ĳ��˲�Ϊ0����ô�ú��Ӿ���Ϊ�˿���
						if(adult_inOahu!=0)
							adult_can_go=true;
						bg.ChildRideToMolokai();
						is_on_Oahu=false;
						boat_Oahu=false;
						children_inOahu--;
						children_inMolokai++;
						isPilot=true;	//�Ѵ�����ȥ�󣬺�����Molokai��������������
						if(!isFinish){
							//Molokai���ϻ���һ������
							children_condition_Molokai.wake();
						}
						//Molokai���ϵĵ�ǰ��������
						children_condition_Molokai.sleep();
					}
				}
				
				
				else{
					//�������Oahu,���ӻ���ȥOahu
					bg.ChildRowToOahu();
					is_on_Oahu=true;
					boat_Oahu=true;
					children_inMolokai--;
					children_inOahu++;
					
					if(adult_inOahu==0){
						//��� ��Oahu�ĳ�������Ϊ0,����˲�����,�Ѻ��ӻ���
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
