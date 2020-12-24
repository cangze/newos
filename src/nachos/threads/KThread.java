package nachos.threads;

import nachos.machine.*;

/**
 * A KThread is a thread that can be used to execute Nachos kernel code. Nachos
 * allows multiple threads to run concurrently.
 * <p>
 * To create a new thread of execution, first declare a class that implements
 * the <tt>Runnable</tt> interface. That class then implements the <tt>run</tt>
 * method. An instance of the class can then be allocated, passed as an argument
 * when creating <tt>KThread</tt>, and forked. For example, a thread that
 * computes pi could be written as follows:
 *
 * <p>
 * <blockquote>
 *
 * <pre>
 * class PiRun implements Runnable {
 *     public void run() {
 *         // compute pi
 *         ...
 *     }
 * }
 * </pre>
 *
 * </blockquote>
 * <p>
 * The following code would then create a thread and start it running:
 *
 * <p>
 * <blockquote>
 *
 * <pre>
 * PiRun p = new PiRun();
 * new KThread(p).fork();
 * </pre>
 *
 * </blockquote>
 */

public class KThread {
    /**
     * Get the current thread.
     *
     * @return the current thread.
     */
    public static KThread currentThread() {
        // 返回当前KThread
        Lib.assertTrue(currentThread != null);
        return currentThread;
    }

    /**
     * Allocate a new <tt>KThread</tt>. If this is the first <tt>KThread</tt>,
     * create an idle thread as well.
     */
    public KThread() {
        // start
        // waitJoinQueue = ThreadedKernel.scheduler.newThreadQueue(true);
        // end
        boolean status = Machine.interrupt().disable();
        if (currentThread != null) {
            tcb = new TCB();
        } else {
            //currentThread是空的时候，实例化readyQueue
            readyQueue = ThreadedKernel.scheduler.newThreadQueue(false);
            readyQueue.acquire(this);

            currentThread = this;
            tcb = TCB.currentTCB();// 第一个线程是主线程，指向第一个TCB
            name = "main";
            restoreState();// 对主线程置运行状态

            createIdleThread();
        }
        waitQueue.acquire(this);
        Machine.interrupt().restore(status);

    }

    /**
     * Allocate a new KThread.
     *
     * @param target the object whose <tt>run</tt> method is called.
     */
    public KThread(Runnable target) {
        this();
        this.target = target;
    }

    /**
     * Set the target of this thread.
     *
     * @param target the object whose <tt>run</tt> method is called.
     * @return this thread.
     */
    public KThread setTarget(Runnable target) {
        Lib.assertTrue(status == statusNew);

        this.target = target;
        return this;
    }

    /**
     * Set the name of this thread. This name is used for debugging purposes
     * only.
     *
     * @param name the name to give to this thread.
     * @return this thread.
     */
    public KThread setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Get the name of this thread. This name is used for debugging purposes
     * only.
     *
     * @return the name given to this thread.
     */
    public String getName() {
        return name;
    }

    /**
     * Get the full name of this thread. This includes its name along with its
     * numerical ID. This name is used for debugging purposes only.
     *
     * @return the full name given to this thread.
     */
    public String toString() {
        return (name + " (#" + id + ")");
    }

    /**
     * Deterministically and consistently compare this thread to another thread.
     */
    public int compareTo(Object o) {
        KThread thread = (KThread) o;

        if (id < thread.id)
            return -1;
        else if (id > thread.id)
            return 1;
        else
            return 0;
    }

    /**
     * Causes this thread to begin execution. The result is that two threads are
     * running concurrently: the current thread (which returns from the call to
     * the <tt>fork</tt> method) and the other thread (which executes its
     * target's <tt>run</tt> method).
     */
    public void fork() {
        // 执行KThread
        Lib.assertTrue(status == statusNew);
        Lib.assertTrue(target != null);

        Lib.debug(dbgThread, "Forking thread: " + toString() + " Runnable: " + target);
        boolean intStatus = Machine.interrupt().disable();
        // 关中断，在线程将要执行的准备阶段不能被打断
        //start如果不是主线程，则开始执行的时候会被interrupt
        tcb.start(new Runnable() {
            public void run() {
                runThread();
            }
        });

        ready();// 并未真正开始执行，只是将线程移动到ready队列

        Machine.interrupt().restore(intStatus);// 回到机器原来的状态。
    }

    private void runThread() {
        begin();
        target.run();// 执行target
        finish();
    }

    private void begin() {
        Lib.debug(dbgThread, "Beginning thread: " + toString());

        Lib.assertTrue(this == currentThread);

        restoreState();

        Machine.interrupt().enable();// 开中断
    }

    /**
     * Finish the current thread and schedule it to be destroyed when it is safe
     * to do so. This method is automatically called when a thread's
     * <tt>run</tt> method returns, but it may also be called directly.
     * <p>
     * The current thread cannot be immediately destroyed because its stack and
     * other execution state are still in use. Instead, this thread will be
     * destroyed automatically by the next thread to run, when it is safe to
     * delete this thread.
     */
    /**
     * finish是每个线程执行结束时必会执行的方法。
     * */
    public static void finish() {
        Lib.debug(dbgThread, "Finishing thread: " + currentThread.toString());
// 关中断
        Machine.interrupt().disable();

        // 将TCB变成将要结束的TCB
        Machine.autoGrader().finishingCurrentThread();

        //
        Lib.assertTrue(toBeDestroyed == null);

        // 将当前的线程变成将要结束的线程，下一个线程运行时消除它
        toBeDestroyed = currentThread;

        // 当前线程状态置为完成
        currentThread.status = statusFinished;

        // 从当前线程的waitQueue中得到下一个线程，放入就绪队列
        KThread thread = currentThread().waitQueue.nextThread();

        if (thread != null) {
            thread.ready();
        }

        //当前线程睡眠
        if(currentThread.getName().contains("thread")) {
            System.out.println(currentThread.getName() + " is finished");
        }
        sleep();

    }

    /**
     * Relinquish the CPU if any other thread is ready to run. If so, put the
     * current thread on the ready queue, so that it will eventually be
     * rescheuled.
     *
     * <p>
     * Returns immediately if no other thread is ready to run. Otherwise returns
     * when the current thread is chosen to run again by
     * <tt>readyQueue.nextThread()</tt>.
     *
     * <p>
     * Interrupts are disabled, so that the current thread can atomically add
     * itself to the ready queue and switch to the next thread. On return,
     * restores interrupts to the previous state, in case <tt>yield()</tt> was
     * called with interrupts disabled.
     */
    public static void yield() {
        // 运行线程放弃CPU，将当前线程放入就绪队列，读取就绪队列下一个线程
        Lib.debug(dbgThread, "Yielding thread: " + currentThread.toString());

        Lib.assertTrue(currentThread.status == statusRunning);

        boolean intStatus = Machine.interrupt().disable();
        // 正在执行的线程放入就绪队列执行就绪队列的下一个线程
        currentThread.ready();
        // 运行下一个线程
        runNextThread();

        Machine.interrupt().restore(intStatus);
    }

    /**
     * Relinquish the CPU, because the current thread has either finished or it
     * is blocked. This thread must be the current thread.
     *
     * <p>
     * If the current thread is blocked (on a synchronization primitive, i.e. a
     * <tt>Semaphore</tt>, <tt>Lock</tt>, or <tt>Condition</tt>), eventually
     * some thread will wake this thread up, putting it back on the ready queue
     * so that it can be rescheduled. Otherwise, <tt>finish()</tt> should have
     * scheduled this thread to be destroyed by the next thread to run.
     */
    public static void sleep() {
        // 如果线程执行完，则是从finish来，否则线程锁死，读取下一个线程
        Lib.debug(dbgThread, "Sleeping thread: " + currentThread.toString());
        Lib.assertTrue(Machine.interrupt().disabled());
        if (currentThread.status != statusFinished)
            currentThread.status = statusBlocked;
        runNextThread();
    }

    /**
     * Moves this thread to the ready state and adds this to the scheduler's
     * ready queue.
     */
    public void ready() {
        // 将线程移动到ready队列
        Lib.debug(dbgThread, "Ready thread: " + toString());
        Lib.assertTrue(Machine.interrupt().disabled());
        Lib.assertTrue(status != statusReady);

        status = statusReady;
        if (this != idleThread)
            readyQueue.waitForAccess(this);
        // 将线程移入队列，idle线程不用放入等待队列
        Machine.autoGrader().readyThread(this);
    }

    /**
     * Waits for this thread to finish. If this thread is already finished,
     * return immediately. This method must only be called once; the second call
     * is not guaranteed to return. This thread must not be the current thread.
     */
    public void join() {
        // 线程B中有A.join()语句，则B等待A执行完才能执行

        Lib.debug(dbgThread, "Joining to thread: " + toString());

        //判断是不是 A调用了A.join()--自己调用自己的join
        Lib.assertTrue(this != currentThread);

        //判断当前线程是不是调用过join
        Lib.assertTrue(join_counter == 0);

        //如果不是，则让join_counter++
        join_counter++;

        //关中断并返回上一个的中断状态---关中断的意思就是不让代码被打断--保持事务的原子性
        boolean status = Machine.interrupt().disable();

        //如果调用join()的对象的status不为完成状态
        if (this.status != statusFinished) {

            //让this线程成为waitQueue队列的头，表明只有先执行this线程，才会去执行队列里的线程
            waitQueue.acquire(this);

            //将当前进程加入到waitQueue中
            waitQueue.waitForAccess(KThread.currentThread());

            //将当前线程睡眠
            currentThread.sleep();
        }

        //yield() finished-->ready()

        //如果是Finish状态则直接返回

        //开中断
        Machine.interrupt().restore(status);

    }

    /**
     * Create the idle thread. Whenever there are no threads ready to be run,
     * and <tt>runNextThread()</tt> is called, it will run the idle thread. The
     * idle thread must never block, and it will only be allowed to run when all
     * other threads are blocked.
     *
     * <p>
     * Note that <tt>ready()</tt> never adds the idle thread to the ready set.
     */
    private static void createIdleThread() {
        // 创建idle线程
        Lib.assertTrue(idleThread == null);

        idleThread = new KThread(new Runnable() {
            public void run() {
                while (true)
                    yield();
            }
            // idle线程一直执行的操作时yield（放弃CPU）
        });
        idleThread.setName("idle");

        Machine.autoGrader().setIdleThread(idleThread);

        idleThread.fork();
    }

    /**
     * Determine the next thread to run, then dispatch the CPU to the thread
     * using <tt>run()</tt>.
     */
    private static void runNextThread() {// 执行下一个线程
        KThread nextThread = readyQueue.nextThread();
        if (nextThread == null)
            nextThread = idleThread;// 如果线程队列为空则执行idle线程
        nextThread.run();
    }

    /**
     * Dispatch the CPU to this thread. Save the state of the current thread,
     * switch to the new thread by calling <tt>TCB.contextSwitch()</tt>, and
     * load the state of the new thread. The new thread becomes the current
     * thread.
     *
     * <p>
     * If the new thread and the old thread are the same, this method must still
     * call <tt>saveState()</tt>, <tt>contextSwitch()</tt>, and
     * <tt>restoreState()</tt>.
     *
     * <p>
     * The state of the previously running thread must already have been changed
     * from running to blocked or ready (depending on whether the thread is
     * sleeping or yielding).
     * <p>
     * finishing
     * <tt>true</tt> if the current thread is finished, and should be
     * destroyed by the new thread.
     */
    private void run() {
        Lib.assertTrue(Machine.interrupt().disabled());

        Machine.yield();// 当前java线程放弃CPU

        currentThread.saveState();// 保存状态

        Lib.debug(dbgThread, "Switching from: " + currentThread.toString() + " to: " + toString());

        currentThread = this;

        tcb.contextSwitch();

        currentThread.restoreState();
    }

    /**
     * Prepare this thread to be run. Set <tt>status</tt> to
     * <tt>statusRunning</tt> and check <tt>toBeDestroyed</tt>.
     */
    protected void restoreState() {
        // 恢复状态，执行此线程，如果有要结束的线程就结束它
        Lib.debug(dbgThread, "Running thread: " + currentThread.toString());

        Lib.assertTrue(Machine.interrupt().disabled());
        Lib.assertTrue(this == currentThread);
        Lib.assertTrue(tcb == TCB.currentTCB());

        Machine.autoGrader().runningThread(this);

        status = statusRunning;

        if (toBeDestroyed != null) {
            toBeDestroyed.tcb.destroy();
            toBeDestroyed.tcb = null;
            toBeDestroyed = null;
        }
    }

    /**
     * Prepare this thread to give up the processor. Kernel threads do not need
     * to do anything here.
     */
    protected void saveState() {
        Lib.assertTrue(Machine.interrupt().disabled());
        Lib.assertTrue(this == currentThread);
    }

    private static class PingTest implements Runnable {
        PingTest(int which) {
            this.which = which;
        }

        public void run() {
            for (int i = 0; i < 5; i++) {
                System.out.println("*** thread " + which + " looped " + i + " times");
               // System.out.println("thread "+which+" yield");
                currentThread.yield();
            }
        }

        private int which;
    }

    public static class joinTest implements Runnable {
        private KThread thread1;

        public joinTest(KThread thread1) {
            this.thread1 = thread1;
        }

        public void run() {
            System.out.println("I will call join(),and wait for thread1 execute over!");
            thread1.join();
            currentThread.yield();
            System.out.println("As you can see ,after thread1 loops 5 times,I procceed ");
            System.out.println("successful");
        }
    }

    /**
     * Tests whether this module is working.
     */

    public static void test_join() {
        Lib.debug(dbgThread, "Enter KThread.selfTest");
        boolean ints = Machine.interrupt().disable();
        System.out.println("-----Now we begin to test join()!-----");
        //fork只是将它们放到就绪队列并未开始执行
        KThread kThreada=new KThread(new PingTest(1));
        kThreada.setName("thread 1");

//        KThread kThreadb=new KThread(new PingTest(2));
//        new KThread(new Runnable() {
//            @Override
//            public void run() {
//
//            }
//        })

        KThread kThreadb=new KThread(new Runnable() {
            @Override
            public void run() {
                System.out.println("thread0 is started");
                System.out.println("调用join方法，当前线程阻塞，thread1执行结束后thread0开始执行");
                kThreada.join();
            }
        });
        kThreadb.setName("thread 0");

        //fork加入到就绪队列
        kThreadb.fork();
        kThreada.fork();

        Machine.interrupt().restore(ints);
    }
//    public static void joinSelfTest(){
//        final KThread a = new KThread(new PingTest(1));
//        System.out.println("thread 1 启动");
//        a.fork();
//        System.out.println("调用join方法，当前线程阻塞，thread 1 执行结束后thread 0 再执行【thread 0 为主线程】");
//        a.join();
//        System.out.println("thread 0 开始执行");
//        new PingTest(0).run();
//    }
    public static void test_condition2() {
        Lib.debug(dbgThread, "Enter KThread.selfTest");

        Lock lock = new Lock();
        Condition2 condition2 = new Condition2(lock);
        //形成新的A线程
        KThread thread_A = new KThread(new Runnable() {
            public void run() {
                //threadA获得锁
                lock.acquire();
                System.out.println("-----Now we begin to test condition2()!-----");
                System.out.println("thread_A will sleep");
                //threadA睡眠，睡眠时释放锁
                condition2.sleep();

                //threadA被唤醒
                System.out.println("thread_A is waked up");
                //threadA释放锁
                lock.release();
                System.out.println("thread_A execute successful!");
            }
        });

        KThread thread_B = new KThread(new Runnable() {
            public void run() {
                lock.acquire();
                System.out.println("thread_B will sleep");
                condition2.sleep();

                System.out.println("thread_B is waked up");
                System.out.println("thread_B execute successful!");
            }
        });

        KThread thread_MM = new KThread(new Runnable() {
            public void run() {

                lock.acquire();
                System.out.println("Thread_Wake:I will wake up all of the threads");

                //获得锁之后唤醒所有线程
                condition2.wakeAll();

                //释放锁
                lock.release();
                System.out.println("thread_Wake execute successful!");

            }
        });

        //list A,B

        thread_A.setName("threadA");
        thread_B.setName("threadB");
        thread_MM.setName("threadMM");

        thread_A.fork();
        thread_B.fork();
        thread_MM.fork();

    }

    public static void test_Alarm() {
        KThread alarmThread_1 = new KThread(new Runnable() {
            int wait = 10;
            public void run() {
                System.out.println("-----Now we begin to test Alarm()-----");
                System.out.println("alarmThread_1进入睡眠,时间:" + Machine.timer().getTime() + "等待时间:" + wait);
                ThreadedKernel.alarm.waitUntil(wait);
                System.out.println("alarmThread_1执行结束");
            }
        }).setName("alarmThread_1");

        KThread alarmThread_2 = new KThread(new Runnable() {
            int wait = 300;

            public void run() {
                System.out.println("alarmThread_2进入睡眠,时间:" + Machine.timer().getTime() + "等待时间:" + wait);
                ThreadedKernel.alarm.waitUntil(wait);
                System.out.println("alarmThread_2执行结束后");
				System.out.println("successful");
            }
        }).setName("alarmThread_2");

        alarmThread_1.fork();
        alarmThread_2.fork();
        System.out.println("successful");

    }

    public static void test_communicator() {
//		Lib.debug(dbgThread, message);
        System.out.println("-----Now we begin to test Communicator()-----");
        Communicator communicator = new Communicator();
        KThread s1 = new KThread(new Runnable() {
            public void run() {
                communicator.speak(985);
                System.out.println("Speak has spoken 985!");
            }
        });
        KThread s2 = new KThread(new Runnable() {
            public void run() {
                communicator.speak(211);
                System.out.println("Speak has spoken 211!");
            }
        });

        KThread l1 = new KThread(new Runnable() {
            public void run() {
                int hear = communicator.listen();
                System.out.println("listen has heared " + hear);
            }
        });

        KThread l2 = new KThread(new Runnable() {
            public void run() {
                int hear = communicator.listen();
                System.out.println("listen has heared " + hear);
                System.out.println("communicator successful!");
            }
        });

        l1.setName("listner1");
        l2.setName("listner2");
        s1.setName("speaker1");
        s2.setName("speaker2");




        s1.fork();
        l2.fork();
        l1.fork();
        s2.fork();
    }

    public static void test_Priority() {
        System.out.println("-----Now begin the test_Priority()-----");
        KThread thread1 = new KThread(new Runnable() {
            public void run() {
                for (int i = 0; i < 3; i++) {
                    KThread.currentThread.yield();
                    System.out.println("thread1");

                }
            }
        });

        KThread thread2 = new KThread(new Runnable() {
            public void run() {

                for (int i = 0; i < 3; i++) {
                    KThread.currentThread.yield();
                    System.out.println("thread2");

                }
            }
        });

        KThread thread3 = new KThread(new Runnable() {
            public void run() {
                thread1.join();
                for (int i = 0; i < 3; i++) {
                    KThread.currentThread.yield();
                    System.out.println("thread3");
                }
            }
        });

        boolean status = Machine.interrupt().disable();
        thread1.setName("thread-1");
        thread2.setName("thread-2");
        thread3.setName("thread-3");

        ThreadedKernel.scheduler.setPriority(thread1, 2);
        ThreadedKernel.scheduler.setPriority(thread2, 4);
        ThreadedKernel.scheduler.setPriority(thread3, 2);

        Machine.interrupt().restore(status);

        thread1.fork();
        thread2.fork();
        thread3.fork();

//        thread4.fork();
//        thread5.fork();
//        thread6.fork();


    }

    public static void test_Boat() {
        Lib.debug(dbgThread, "Enter KThread.selfTest");
        System.out.println("-----Boat test begin-----");

        new KThread(new Runnable() {
            public void run() {
                Boat.selfTest();
            }
        }).fork();
        System.out.println("Successful");
    }

    public static void test_Lottery() {
        System.out.println("-----Now begin the test_LotteryPriority()-----");
        KThread thread1 = new KThread(new Runnable() {
            public void run() {
                for (int i = 0; i < 3; i++) {
                    KThread.currentThread.yield();
                    System.out.println("thread-1");

                }
            }
        });

        KThread thread2 = new KThread(new Runnable() {
            public void run() {
                for (int i = 0; i < 3; i++) {
                    KThread.currentThread.yield();
                    System.out.println("thread-2");

                }
            }
        });

        KThread thread3 = new KThread(new Runnable() {
            public void run() {
                thread1.join();
                for (int i = 0; i < 3; i++) {
                    KThread.currentThread.yield();
                    System.out.println("thread-3");
                }
            }
        });
        boolean status = Machine.interrupt().disable();
        ThreadedKernel.scheduler.setPriority(thread1, 1);
        ThreadedKernel.scheduler.setPriority(thread2, 2);
        ThreadedKernel.scheduler.setPriority(thread3, 6);
        thread1.setName("thread-1");
        thread2.setName("thread-2");
        thread3.setName("thread-3");

        Machine.interrupt().restore(status);
        thread1.fork();
        thread2.fork();
        thread3.fork();
    }

    public static void selfTest() {
        Lib.debug(dbgThread, "Enter KThread.selfTest");

//		new KThread(new PingTest(1)).setName("forked thread").fork();
//		new PingTest(0).run();
//		test_join();
//		test_condition2();
//		test_Alarm();
//		test_communicator();
//        test_Priority();
//      test_Boat();
//	    test_Lottery();
 //       SpeakTest();
    }
    private static class Speaker implements Runnable {
        private Communicator c;
        Speaker(Communicator c) {
            this.c = c;
        }
        public void run() {
            for (int i = 0; i < 5; ++i) {
                System.out.println("speaker speaking" + i);
                c.speak(i);
                //System.out.println("speaker spoken");
                KThread.yield();
            }
        }
    }
    public static void SpeakTest() {
        System.out.println("测试Communicator类：");
        Communicator c = new Communicator();
        new KThread(new Speaker(c)).setName("Speaker").fork();
        for (int i = 0; i < 5; ++i) {
            System.out.println("listener listening " + i);
            int x = c.listen();
            System.out.println("listener listened, word = " + x);
            KThread.yield();
        }
    }
    private static final char dbgThread = 't';

    /**
     * Additional state used by schedulers.
     *
     * @see nachos.threads.PriorityScheduler.ThreadState
     */
    public Object schedulingState = null;
    private ThreadQueue waitQueue = ThreadedKernel.scheduler.newThreadQueue(true);

    private static final int statusNew = 0;
    private static final int statusReady = 1;
    private static final int statusRunning = 2;
    private static final int statusBlocked = 3;
    private static final int statusFinished = 4;

    /**
     * The status of this thread. A thread can either be new (not yet forked),
     * ready (on the ready queue but not running), running, or blocked (not on
     * the ready queue and not running).
     */
    private int status = statusNew;
    private String name = "(unnamed thread)";
    private Runnable target;
    private TCB tcb;

    /**
     * Unique identifer for this thread. Used to deterministically compare
     * threads.
     */
    private int id = numCreated++;
    /**
     * Number of times the KThread constructor was called.
     */
    private static int numCreated = 0;
    // start
    private int join_counter = 0;

    // end
    private static ThreadQueue readyQueue = null;
    private static KThread currentThread = null;
    private static KThread toBeDestroyed = null;
    private static KThread idleThread = null;
}
