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
	private Lock lock=null;//������

	//˵����������
	private int speakerNum;
	private int listenerNum;

	//��Ϣ����
	private int word;
	//˵��������������
	private Condition2 speaker;
	private Condition2 listener;

	//���ݶ���
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
			System.out.println("��ʱû�������ߣ��ȴ�����");
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
		//�����
		lock.acquire();
		if(listenerNum==0){
			//�������Ϊ0,��Ҫ��speaker�������
			speakerNum++;
			//��Ҫ�����int��������β��
			queue.offer(word);
			//�ȴ������߻���
			speaker.sleep();

		//	System.out.println("speaker������");
//			listener.wake();
			listenerNum--;

		}
		else{
			//������߲�Ϊ0,ֱ�ӻ�������
			speakerNum++;

			queue.offer(word);
			listener.wake();
			//�����Ѻ� �����߼�1
			listenerNum--;
		}
		//�ͷ���
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
		//�������˵��,����˵��˵�����õ�word�����߷���word���������ߵȴ�
		boolean status = Machine.interrupt().disable();
		lock.acquire();
		if(speakerNum!=0){
			//���˵���߲�Ϊ0����wakeһ��speaker˵��
			listenerNum++;
//
			speaker.wake();
			speakerNum--;
			//��������sleep
//			listener.sleep();

		}
		else{
			//���û��speaker,��lister++
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
			System.out.println("��ʱû��˵���ߣ��ȴ�˵��");
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
