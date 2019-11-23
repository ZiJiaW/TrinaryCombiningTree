import java.util.Stack;

class Node {
    enum CStatus{IDLE, FIRST, SECOND, THIRD, RESULT, ROOT};
    boolean locked;
    int countNodes;
    CStatus cStatus;
    int firstValue = -1, secondValue = -1, thirdValue = -1;
    int result;
    int thirdResult;
    Node parent;
    public Node() {
        cStatus = CStatus.ROOT;
        locked = false;
        countNodes = 0;
    }
    public Node(Node myParent) {
        cStatus = CStatus.IDLE;
        parent = myParent;
        locked = false;
        countNodes = 0;
    }

    synchronized boolean precombine() {
        while (locked || countNodes == 2) {
            //System.out.println("wait 1");
            try {
                wait();
            } catch (InterruptedException ex) {
                System.out.println(ex.getMessage());
            }
            //System.out.println("end 1");
        }
        switch (cStatus) {
            case IDLE:
                cStatus = CStatus.FIRST;
                return true;
            case FIRST:
                cStatus = CStatus.SECOND;
                countNodes = 1;
                return false;
            case SECOND:
                cStatus = CStatus.THIRD;
                countNodes = 2;
                //locked = true;
                return false;
            case ROOT:
                return false;
            default:
                System.out.println("Unexpected status in precombination!" + cStatus);
                System.exit(-1);
                return false;
        }
    }

    synchronized int combine(int combined) {
        locked = true;
        while (countNodes != 0) {
            //System.out.println("wait 2 " + cStatus);
            try {
                wait();
            } catch (InterruptedException ex) {
                System.out.println(ex.getMessage());
            }
            //System.out.println("end 2 " + cStatus);
        }
        locked = true;
        firstValue = combined;
        switch (cStatus) {
            case FIRST:
                return firstValue;
            case SECOND:
                return firstValue + secondValue;
            case THIRD:
                countNodes = 2;
                return firstValue + secondValue + thirdValue;
            default:
                System.out.println("Unexpected status in combination!" + cStatus);
                System.exit(-2);
                return -1;
        }
    }

    synchronized int op(int combined) {
        switch (cStatus) {
            case ROOT:
                int prior = result;
                result += combined;
                return prior;
            case SECOND:
                locked = true;
                secondValue = combined;
                countNodes = 0;
                notifyAll();
                while (cStatus != CStatus.RESULT) {
                    try {
                        wait();
                    } catch (InterruptedException ex) {
                        System.out.println(ex.getMessage());
                    }
                }
                locked = false;
                firstValue = secondValue = thirdValue = -1;
                cStatus = CStatus.IDLE;
                notifyAll();
                return result;
            case THIRD:
                locked = true;
                boolean isSecond = (secondValue == -1);
                if (isSecond) {
                    secondValue = combined;
                } else {
                    thirdValue = combined;
                }
                //System.out.printf("second: %d, third: %d, countNode: %d\n", secondValue, thirdValue, countNodes);
                countNodes--;
                if (countNodes == 0) {
                    //System.out.println("notify all " + cStatus);
                    notifyAll();
                }
                while (cStatus != CStatus.RESULT) {
                    //System.out.printf("wait result third: %s\n", cStatus);
                    try {
                        wait();
                    } catch (InterruptedException ex) {
                        System.out.println(ex.getMessage());
                    }
                    //System.out.printf("end result third: %s\n", cStatus);
                }
                countNodes--;
                //System.out.println(countNodes);
                if (countNodes == 0) {
                    locked = false;
                    firstValue = secondValue = thirdValue = -1;
                    cStatus = CStatus.IDLE;
                    //System.out.printf("notify idle, result: %d, thirdResult: %d\n", result, thirdResult);
                    notifyAll();
                }
                return isSecond ? result : thirdResult;
            default:
                System.out.println("Unexpected status in operation!"+cStatus);
                System.exit(-3);
                return -1;
        }
    }

    synchronized void distribute(int prior) {
        switch (cStatus) {
            case FIRST:
                cStatus = CStatus.IDLE;
                firstValue = secondValue = thirdValue = -1;
                locked = false;
                break;
            case SECOND:
                result = prior + firstValue;
                cStatus = CStatus.RESULT;
                break;
            case THIRD:
                result = prior + firstValue;
                thirdResult = result + secondValue;
                cStatus = CStatus.RESULT;
                break;
            default:
                System.out.println("Unexpected status in distribution!"+cStatus);
                System.exit(-4);
        }
        notifyAll();
    }
}

public class CombinationCounter {
    Node[] nodes;
    Node[] leaf;

    private int pow(int a, int i) {
        int res = 1;
        for (int k = 0; k < i; k++) {
            res *= a;
        }
        return res;
    }

    public CombinationCounter(int depth) {
        int length = (pow(3, depth)-1)/2;
        nodes = new Node[length];
        nodes[0] = new Node();
        for (int i = 1; i < length; i++) {
            nodes[i] = new Node(nodes[(i-1)/3]);
        }
        leaf = new Node[pow(3, depth-1)];
        for (int i = 0; i < leaf.length; i++) {
            leaf[i] = nodes[length - i - 1];
        }
    }

    public int getAndIncrement(int threadId) {
        Stack<Node> stack = new Stack<>();
        Node myLeaf = leaf[threadId];
        Node node = myLeaf;
        while (node.precombine()) {
            node = node.parent;
        }
        //System.out.printf("Thread %d after precombine;\n", threadId);
        Node stop = node;
        node = myLeaf;
        int combined = 1;
        while (node != stop) {
            combined = node.combine(combined);
            stack.push(node);
            node = node.parent;
        }
        //System.out.printf("Thread %d after combine;\n", threadId);
        int prior = stop.op(combined);
        while (!stack.empty()) {
            node = stack.pop();
            node.distribute(prior);
        }
        //System.out.printf("Thread %d return %d;\n", threadId, prior);
        return prior;
    }

}
