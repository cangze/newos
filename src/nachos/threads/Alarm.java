package nachos.threads;

import java.lang.management.ThreadInfo;
import java.util.LinkedList;

import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption�����ȣ�, and to allow threads to sleep
 * until a certain��һ���� time.
 */
public class Alarm {
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler��ʱ���жϴ������ to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function�����У� correctly with more than one
     * alarm.
     */

	private LinkedList<ThreadInfo> list = new LinkedList();
	/**
	 *�洢�ȴ��̵߳���Ϣ�������̺߳ź͵ȴ�ʱ��
	 *�ڲ���,����߳���Ϣ
	 */
	private class ThreadInfo{
		private KThread thread;
		private long time;//���Ա����ѵ�ʱ��
		public ThreadInfo(KThread thread,long time){
			this.thread=thread;
			this.time=time;
		}
		public KThread getThread() {
			return thread;
		}
		public void setThread(KThread thread) {
			this.thread = thread;
		}
		public long getTime() {
			return time;
		}
		public void setTime(long time) {
			this.time = time;
		}
	}


	public Alarm() {
	Machine.timer().setInterruptHandler(new Runnable() {
		public void run() { timerInterrupt(); }
	    });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically�������Եģ� (approximately����Լ�� every 500 clock ticks). Causes the current
     * thread to yield, forcing��ǿ�ƣ� a context switch if there is another thread
     * that should be run.
     * ��ÿһ��timer����ʱ���ж�ʱ �������У��������е�ʱ��״̬�����̵߳��˵ȴ���ʱ��
     * �Ͱ��̴߳Ӷ�����ȡ������������С�
     */
    public void timerInterrupt() {//����ʱ���ж�
    	boolean status=Machine.interrupt().disable();
    	long currentTime=Machine.timer().getTime();
    	int size = list.size();
    	//���ȴ������߳�����Ϊ��ʱ�������κ�����
		if(size!=0) {
//			System.out.println(list);
			int i;
			for(i=0;i<size;i++) {
//				System.out.println("i:"+i);
				//��õ�ǰʱ��
				long currenttime=Machine.timer().getTime();
				long targetwaittime=list.get(i).getTime();

				KThread thread=list.get(i).getThread();
//				System.out.println("��ǰʱ��Ϊ:"+currenttime+";"+thread.getName()+"�Ļ���ʱ��Ϊ:"+targetwaittime);
				if(currenttime>=targetwaittime) {
//    				KThread thread=waitlist.get(i).thread;
					//����ǰ���̼��뵽��������
					thread.ready();
//					System.out.println(thread.getName()+":�����������");
					//�Ѹ��̴߳�waitlist��ɾ��
					list.remove(i);

					//��ͷ����
					size--;
//					System.out.println(list);

					if(size!=0) {
//						System.out.println("list(0)="+list.get(i).getThread().getName());
						i=-1;
					}

				}
				else {
					break;
				}
			}
		}
    	KThread.currentThread().yield();
    	Machine.interrupt().restore(status);
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */

    public void waitUntil(long x) {
    	//����ȴ�ʱ��
    	boolean status=Machine.interrupt().disable();
	// for now, cheat just to get something working (busy waiting is bad)
    	//���㱻���ѵ�ʱ��
    	long wakeTime = Machine.timer().getTime() + x;

    	//����һ���߳���Ϣ����
    	ThreadInfo threadWake=new ThreadInfo(KThread.currentThread(),wakeTime);
    	int size = list.size();
    	//��������ThreadInfo���տ��Ա����ѵ�˳�����

		//thread1 150; thread2 70;
		//thread2,thread1
    	if(size==0)list.add(threadWake);

    	//Ѱ��һ�����ʵ�λ�ý� KThreadInfo����
    	else{
    		//��������Ѱ��һ�� waketime ���ʵ�λ��
    		for(int i=0;i<size;i++){
    			if(wakeTime<list.get(i).getTime()){
    				list.add(i, threadWake);
    				break;
    			}
    			if(i==size-1)
    				list.add(threadWake);
    		}
    	}
    	//System.out.println(KThread.currentThread().getName()+"�߳�˯�ߣ�ʱ��:"+Machine.timer().getTime()+",����"+wakeTime+"����");

		//˯��
		KThread.currentThread().sleep();
    	//���ж�
    	Machine.interrupt().restore(status);
    }


    
    


}
