import java.util.Random;

public class Deopt {

  public static void main(String[] args) throws InterruptedException {
    int j = 0;
            for (int i = 0; i < 1_000; i++)
            j += doIt(new B());



        System.out.println("waiting for compiler" + j);

        // Thread.sleep(5_000);

        System.out.println("deop");
        System.out.println(C.class);

        for (int i = 0; i < 1_000; i++)
            j += doIt(new C());

        System.out.println("deop" + j);
  }

      private static int doIt(A a) {
        int r = 0;
        for (int i = 0; i < 100_000; i++) {
            r += a.doIt();
        }
        return r;
    }
}


    interface A {
        public int doIt();
    }

    class B implements A {
        Random r = new Random();

        @Override
        public int doIt() {
            return r.nextInt();
        }
    }

    class C implements A {
        @Override
        public int doIt() {
            return 1;
        }
    }