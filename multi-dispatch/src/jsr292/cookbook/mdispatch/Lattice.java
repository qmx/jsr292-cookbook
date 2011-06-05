package jsr292.cookbook.mdispatch;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

class Lattice {
  private final List<PositionInfo> positionInfos;
  private final Node root = new Node(null); 
  private int size;
  
  public Lattice(List<PositionInfo> positionInfos) {
    if (positionInfos.isEmpty()) {
      throw new IllegalArgumentException("method need at least one parameter");
    }
      
    this.positionInfos = positionInfos;
  }
  
  public void add(MethodHandle mh) {
    Node node = new Node(mh);
    root.add(positionInfos, node);
    size++;
  }
  
  public MethodHandle[] topologicalSort() {
    MethodHandle[] array = new MethodHandle[size];
    int i = 0;
    Set<Node> breadthFirstMark = breadthFirstMark(root, size);
    for(Node node: breadthFirstMark) {
      array[i++] = node.mh;
    }
    return array;
  }
  
  private static Set<Node> breadthFirstMark(Node root, int capacity) {
    LinkedHashMap<Node, Object> result = new LinkedHashMap<Node, Object>();
    ArrayDeque<Node> queue = new ArrayDeque<Node>(capacity);
    queue.offer(root);
    while(!queue.isEmpty()) {
      Node node = queue.remove();
      node.mark = true;
      for(Node child: node.children) {
        if (child.mark == true) { // already visited
          result.remove(child);
          result.put(child, null);
          continue;
        }
        result.put(child, null);
        queue.offer(child);
      }
    }
    return result.keySet();
  }
  
  static class Node {
    boolean mark;
    MethodHandle mh;
    final LinkedList<Node> children =
      new LinkedList<Node>();
    
    Node(MethodHandle mh) {
      this.mh = mh;
    }
    
    void add(List<PositionInfo> positionInfos, Node newNode) {
      boolean inserted = false;
      for(ListIterator<Node> it = children.listIterator(); it.hasNext();) {
        Node node = it.next();
        switch(diff(positionInfos, node.mh, newNode.mh)) {
        case LT:
          inserted = true;
          node.add(positionInfos, newNode);
          continue;
        case GT:
          it.remove();
          newNode.children.add(node);
          continue;
        case EQ:
          throw new AssertionError();
        case NC:
          continue;
        }
      }
      if (!inserted) {
        children.add(newNode);
      }
    }

    private final static int LT = 1;  // less than
    private final static int GT = 2;  // greater than
    private final static int EQ = 3;  // equal
    private final static int NC = 4;  // no conversion
    
    private static int diff(List<PositionInfo> positionInfos, MethodHandle mh1, MethodHandle mh2) {
      MethodType type1 = mh1.type();
      MethodType type2 = mh2.type();
      
      int bias = EQ;
      for(int i=0; i<positionInfos.size(); i++) {
        int index = positionInfos.get(i).projectionIndex;
        switch(diff(type1.parameterType(index), type2.parameterType(index))) {
        case LT:
          if (bias == GT) {
            return NC;  //FIXME perhaps use another constant
          }
          bias = -1;
          continue;
        case GT:
          if (bias == LT) {
            return NC; //FIXME perhaps use another constant
          }
          bias = GT;
          continue;
        case EQ:
          continue;
        case NC:
          return NC;
        }
      }
      return bias;
    }
    
    static int diff(Class<?> type1, Class<?> type2) {
      if (type1 == type2)
        return EQ;
      if (type1.isPrimitive() && type2.isPrimitive())
        return diffPrimitive(type1, type2);
      if (type1.isAssignableFrom(type2))
        return LT; 
      if (type2.isAssignableFrom(type1))
        return GT; 
      return NC;
    }

    private static final int BOOLEAN = 0;
    private static final int BYTE = 1;
    private static final int CHAR = 2;
    private static final int SHORT = 3;
    private static final int INT = 4;
    private static final int LONG = 5;
    private static final int FLOAT = 6;
    private static final int DOUBLE = 7;
    
    private static int asInt(Class<?> primitive) {
      if (primitive == int.class) {
        return INT;
      }
      if (primitive == double.class) {
        return DOUBLE;
      }
      if (primitive == boolean.class) {
        return BOOLEAN;
      }
      if (primitive == char.class) {
        return CHAR;
      }
      if (primitive == long.class) {
        return LONG;
      }
      if (primitive == byte.class) {
        return BYTE;
      }
      if (primitive == float.class) {
        return FLOAT;
      }
      //if (primitive == short.class) {
        return SHORT;
      //}
    }
    
    private static int diffPrimitive(Class<?> type1, Class<?> type2) {
      return diffs[asInt(type1)][asInt(type2)];
    }
    
    private static final int[][] diffs = {
      //        Z   B   S   C   I   J   F   D   
      /* Z */ { EQ, NC, NC, NC, NC, NC, NC, NC },
      /* B */ { NC, EQ, LT, LT, LT, LT, LT, LT },
      /* S */ { NC, GT, EQ, LT, LT, LT, LT, LT },
      /* C */ { NC, GT, GT, EQ, LT, LT, LT, LT },
      /* I */ { NC, GT, GT, GT, EQ, LT, LT, LT },
      /* J */ { NC, GT, GT, GT, GT, EQ, LT, LT },
      /* F */ { NC, GT, GT, GT, GT, GT, EQ, LT },
      /* D */ { NC, GT, GT, GT, GT, GT, GT, EQ },
    };
  }
}
