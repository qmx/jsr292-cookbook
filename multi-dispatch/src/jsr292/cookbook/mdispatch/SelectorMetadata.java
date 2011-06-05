package jsr292.cookbook.mdispatch;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

class SelectorMetadata {
  private final MethodHandle[] mhs;
  private final ArrayList<PositionInfo> positionInfos;
  
  private SelectorMetadata(MethodHandle[] mhs, ArrayList<PositionInfo> positionInfos) {
    this.mhs = mhs;
    this.positionInfos = positionInfos;
  }
  
  public MethodHandle createMethodHandle(MethodType type) {
    MethodHandle[] mhs = this.mhs;
    
    
    // try to simplify using static information
    int constBits = 0;
    LinkedList<PositionInfo> posInfos = new LinkedList<PositionInfo>(positionInfos);
    for(Iterator<PositionInfo> it = posInfos.iterator(); it.hasNext();) {
      PositionInfo positionInfo = it.next();
      int projectionIndex = positionInfo.projectionIndex;
      Class<?> klass = type.parameterType(projectionIndex);
      if (klass.isPrimitive() ||
          Modifier.isFinal(klass.getModifiers())) {  //FIXME add || a hierarchy check 
        int bits = positionInfo.classIntMap.lookup(klass);
        constBits = (constBits == 0)? bits: constBits & bits;
        it.remove();
        continue;
      }
    }
    
    if (posInfos.isEmpty()) {  // static call
      return mhs[Integer.highestOneBit(constBits)].asType(type);
    }
    
    if (posInfos.size() == 1) {
      // we can pre-calculate all method handles and use the exact invoker
      PositionInfo positionInfo = posInfos.getFirst();
      int projectionIndex = positionInfo.projectionIndex;
      ClassMHMap classMHMap = positionInfo.classIntMap.transfer(projectionIndex, mhs, type);
      
      MethodHandle classMHMapLookup = CLASSMHMAP_LOOKUP.bindTo(classMHMap);
      MethodHandle getMH = MethodHandles.filterReturnValue(OBJECT_GET_CLASS, classMHMapLookup);
      
      if (type.parameterCount() != 1) {
        getMH = MethodHandles.permuteArguments(getMH,
            type.changeReturnType(MethodHandle.class).changeParameterType(projectionIndex, Object.class),
            new int[] { projectionIndex });
      }
      getMH = getMH.asType(type.changeReturnType(MethodHandle.class));
      
      return MethodHandles.foldArguments(
          MethodHandles.exactInvoker(type),
          getMH);
    }
    
    // normalize method handles to the callsite type
    MethodHandle[] array = new MethodHandle[mhs.length];
    for(int i=1; i<array.length; i++) {  // starts at 1 because 0 is null currently FIXME
      array[i] = mhs[i].asType(type);  //FIXME, may throw a WMTE
    }
    
    // construct a tree of method handle, prepend static const bits set if necessary
    MethodHandle bitReducer = BitReducer.getReducer(posInfos.size() + ((constBits!=0)? 1: 0));
    bitReducer = bitReducer.bindTo(array);
    if (constBits != 0) {
      bitReducer = bitReducer.bindTo(constBits);
    }
    
    boolean mayBoxUnbox = false;
    MethodHandle[] filters = new MethodHandle[posInfos.size()];
    for(int i=0; i<filters.length; i++) {
      PositionInfo positionInfo = posInfos.get(i);
      MethodHandle classIntMapLookup = CLASSINTMAP_LOOKUP.bindTo(positionInfo.classIntMap);
      MethodHandle filter = MethodHandles.filterReturnValue(OBJECT_GET_CLASS, classIntMapLookup);
      filters[i] = filter;
      
      mayBoxUnbox |= positionInfo.mayBoxUnbox;
    }
    MethodHandle getMH = MethodHandles.filterArguments(bitReducer, 0, filters);
    
    if (posInfos.size() != type.parameterCount()) {
      getMH = MethodHandles.permuteArguments(getMH,
          //FIXME, don't use generic method here !
          MethodType.genericMethodType(type.parameterCount()).changeReturnType(MethodHandle.class),
          toArray(posInfos));
    }
    getMH = getMH.asType(type.changeReturnType(MethodHandle.class));
    return MethodHandles.foldArguments(
        //FIXME invoker() in jdk7b144 returns a wrong method type, so add asType as a workaround
        (mayBoxUnbox)? MethodHandles.invoker(type).asType(type.insertParameterTypes(0, MethodHandle.class)): MethodHandles.exactInvoker(type),
        getMH);
  }
  
  private static final MethodHandle OBJECT_GET_CLASS;
  private static final MethodHandle CLASSINTMAP_LOOKUP;
  private static final MethodHandle CLASSMHMAP_LOOKUP;
  static {
    Lookup lookup = MethodHandles.publicLookup();
    try {
      OBJECT_GET_CLASS = lookup.findVirtual(Object.class, "getClass",
          MethodType.methodType(Class.class));
      CLASSINTMAP_LOOKUP = lookup.findVirtual(ClassIntMap.class, "lookup",
          MethodType.methodType(int.class, Class.class));
      CLASSMHMAP_LOOKUP = lookup.findVirtual(ClassMHMap.class, "lookup",
          MethodType.methodType(MethodHandle.class, Class.class));
    } catch (ReflectiveOperationException e) {
      throw (AssertionError)new AssertionError().initCause(e);
    }
  }

  public static SelectorMetadata create(List<MethodHandle> mhList) {
    if (mhList.size()>32) {
      throw new UnsupportedOperationException("too much overloads (>32)");
    }
    
    int length = mhList.get(0).type().parameterCount();
    HashSet<Class<?>>[] sets = (HashSet<Class<?>>[])new HashSet<?>[length]; 
    HashSet<Class<?>>[] boxUnboxSets = (HashSet<Class<?>>[])new HashSet<?>[length]; 
    for(int i=0; i<length; i++) {
      sets[i] = new HashSet<Class<?>>();
    }
    
    // find types by position
    for(int i=0; i<mhList.size(); i++) {
      MethodHandle mh = mhList.get(i);
      MethodType type = mh.type();
      
      for(int j=0; j<length; j++) {
        Class<?> klass = type.parameterType(j);
        if (klass.isInterface()) {
          throw new UnsupportedOperationException("interface aren't currently supported");
        }
        sets[j].add(klass);
        
        // supplementary primitive/wrapper set used to handle boxing/unboxing
        HashSet<Class<?>> convSet = PRIMITIVE_CONVERSION_MAP.get(klass);
        if (convSet != null) {
          HashSet<Class<?>> boxUnboxSet = boxUnboxSets[j];
          if (boxUnboxSet == null) { // lazy allocation
            boxUnboxSet = boxUnboxSets[j] = new HashSet<Class<?>>();
          }
          boxUnboxSet.addAll(convSet);
        }
      }
    }
    
    // determine projection
    ArrayList<PositionInfo> positionInfos = new ArrayList<PositionInfo>();
    for(int i=0; i<sets.length; i++) {
      HashSet<Class<?>> set = sets[i];
      if (set.size() == 1) { // only one type
        continue;
      }
      
      // add primitive/wrapper classes
      boolean mayBoxUnbox = boxUnboxSets[i] != null;
      if (mayBoxUnbox) {
        set.addAll(boxUnboxSets[i]);
      }
      
      positionInfos.add(new PositionInfo(i, mayBoxUnbox, new ClassIntMap(set.size())));
    }
    
    // topologically sort method handles 
    Lattice lattice = new Lattice(positionInfos);
    for(MethodHandle mh: mhList) {
      lattice.add(mh);
    }
    MethodHandle[] mhs = lattice.topologicalSort();
    
    // populate ClassIntMaps
    int projectionLength = positionInfos.size();
    for(int i=0; i<projectionLength; i++) {
      PositionInfo positionInfo = positionInfos.get(i);
      int index = positionInfo.projectionIndex;
      HashSet<Class<?>> set = sets[index];
      ClassIntMap map = positionInfo.classIntMap;
      for(Class<?> type: set) {
        int bits = 0;
        for(int j=0; j<mhs.length; j++) {
          if (isAssignablefrom(mhs[j].type().parameterType(index), type)) {
            bits |= 1 << j;
          }
        }
        map.putNoResize(type, bits);
      }
    }
    
    //System.out.println("dispatch "+mhList);
    //System.out.println("position infos "+positionInfos);
    
    // insert a method-not-understood (here null FIXME)
    MethodHandle[] newMhs = new MethodHandle[mhs.length + 1];
    System.arraycopy(mhs, 0, newMhs, 1, mhs.length);
    
    return new SelectorMetadata(newMhs, positionInfos);
  }

  private static boolean isAssignablefrom(Class<?> type1, Class<?> type2) {
    if (type1 == type2) {
      return true;
    }
    
    HashSet<Class<?>> conversionSet = PRIMITIVE_CONVERSION_MAP.get(type1);
    if (conversionSet != null) {  // type1 is a primitive, a wrapper, Object or Number
      if (conversionSet.contains(type2)) {
        return true;  // primitive or boxing conversion
      }
    }
    
    return type1.isAssignableFrom(type2);
  }
  
  private static int[] toArray(List<PositionInfo> positionInfos) {
    int[] array = new int[positionInfos.size()];
    int i = 0;
    for(PositionInfo positionInfo: positionInfos) {
      array[i++] = positionInfo.projectionIndex;
    }
    return array;
  }
  
  
  
  private static final HashMap<Class<?>, HashSet<Class<?>>> PRIMITIVE_CONVERSION_MAP;
  static {
    Class<?>[][] array = new Class<?>[][] {
        { boolean.class, Boolean.class},
        { byte.class, Byte.class},
        { short.class, Short.class, char.class, Character.class, byte.class, Byte.class},
        { char.class, Character.class, short.class, Short.class, byte.class, Byte.class},
        { int.class, Integer.class, char.class, Character.class, short.class, Short.class, byte.class, Byte.class},
        { long.class, Long.class, int.class, Integer.class, char.class, Character.class, short.class, Short.class, byte.class, Byte.class},
        { float.class, Float.class, long.class, Long.class, int.class, Integer.class, char.class, Character.class, short.class, Short.class, byte.class, Byte.class},
        { double.class, Double.class, float.class, Float.class, long.class, Long.class, int.class, Integer.class, char.class, Character.class, short.class, Short.class, byte.class, Byte.class},
    };
    
    HashMap<Class<?>, HashSet<Class<?>>> map =
      new HashMap<Class<?>, HashSet<Class<?>>>();
    for(Class<?>[] classes: array) {
      HashSet<Class<?>> conversionSet = new HashSet<Class<?>>();
      Collections.addAll(conversionSet, classes);
      map.put(classes[0], conversionSet);
      map.put(classes[1], conversionSet);
    }
    
    HashSet<Class<?>> objectConvSet = new HashSet<Class<?>>();
    Collections.addAll(objectConvSet, double.class, Double.class, float.class, Float.class, long.class, Long.class, int.class, Integer.class, char.class, Character.class, short.class, Short.class, byte.class, Byte.class);
    map.put(Number.class, new HashSet<Class<?>>(objectConvSet));
    Collections.addAll(objectConvSet, boolean.class, Boolean.class);
    map.put(Object.class, objectConvSet);
    
    PRIMITIVE_CONVERSION_MAP = map;
  }
}
