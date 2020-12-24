package nachos.threads;

import nachos.machine.Machine;

import java.util.LinkedList;
import java.util.Queue;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
	private Lock lock=null;//互斥锁

	//说者听者数量
	private int speakerNum;
	private int listenerNum;

	//信息计数
	private int word;
	//说者听者条件变量
	private Condition2 speaker;
	private Condition2 listener;

	//数据队列
	private Queue<Integer> queue;
	private LinkedList<Integer> words;
	/**
	 * Allocate a new communicator.
	 */
	public Communicator() {

		lock=new Lock();

		queue=new LinkedList<Integer>();

		speaker=new Condition2(lock);
		listener=new Condition2(lock);
		words=new LinkedList<Integer>();
		word=0;
		speakerNum=0;
		listenerNum=0;
	}

	/**
	 * Wait for a thread to listen through this communicator, and then transfer
	 * <i>word</i> to the listener.
	 *
	 * <p>
	 * Does not return until this thread is paired up with a listening thread.
	 * Exactly one listener should receive <i>word</i>.
	 *
	 * @param	word	the integer to transfer.
	 */

	public void speak(int word) {
		boolean preState = Machine.interrupt().disable();
		lock.acquire();
		words.add(word);
		if (listenerNum == 0) {
			speakerNum++;
			System.out.println("暂时没有收听者，等待收听");
			speaker.sleep();
			listenerNum--;
		} else {
			speakerNum++;
			listener.wake();
			listenerNum--;
		}
		lock.release();
		Machine.interrupt().restore(preState);
	}
	/*public void speak(int word) {

		boolean status = Machine.interrupt().disable();
		//获得锁
		lock.acquire();
		if(listenerNum==0){
			//如果听者为0,需要将speaker加入队列
			speakerNum++;
			//将要传输的int放入链表尾部
			queue.offer(word);
			//等待被听者唤醒
			speaker.sleep();

		//	System.out.println("speaker被唤醒");
//			listener.wake();
			listenerNum--;

		}
		else{
			//如果听者不为0,直接唤醒听者
			speakerNum++;

			queue.offer(word);
			listener.wake();
			//被唤醒后 将听者减1
			listenerNum--;
		}
		//释放锁
		lock.release();
		Machine.interrupt().setStatus(status);
		return;
	}
*/
	/**
	 * Wait for a thread to speak through this communicator, and then return
	 * the <i>word</i> that thread passed to <tt>speak()</tt>.
	 *
	 * @return	the integer transferred.
	 */
	/*public int listen() {
		//如果有人说话,则让说者说话，得到word，听者返回word，否则听者等待
		boolean status = Machine.interrupt().disable();
		lock.acquire();
		if(speakerNum!=0){
			//如果说话者不为0，则wake一个speaker说话
			listenerNum++;
//
			speaker.wake();
			speakerNum--;
			//将此听者sleep
//			listener.sleep();

		}
		else{
			//如果没有speaker,则将lister++
			listenerNum++;

			listener.sleep();

			speakerNum--;

		}
		lock.release();
		Machine.interrupt().restore(status);
		return queue.poll();
	}
*/
	public int listen() {
		boolean preState = Machine.interrupt().disable();
		lock.acquire();
		if(speakerNum==0){
			listenerNum++;
			System.out.println("暂时没有说话者，等待说话");
			listener.sleep();
			speakerNum--;
		}else{
			listenerNum++;
			speaker.wake();
			speakerNum--;
		}
		lock.release();
		Machine.interrupt().restore(preState);
		return words.removeLast();
	}
}
