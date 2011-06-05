package jsr292.cookbook.mdispatch;

class PositionInfo {
  final int projectionIndex;
  final boolean mayBoxUnbox;
  final ClassIntMap classIntMap;
  
  public PositionInfo(int projectionIndex, boolean mayBoxUnbox, ClassIntMap classIntMap) {
    this.projectionIndex = projectionIndex;
    this.mayBoxUnbox = mayBoxUnbox;
    this.classIntMap = classIntMap;
  }
  
  @Override
  public String toString() {
    return "(" + projectionIndex + ", " + ((mayBoxUnbox)? "mayBoxUnbox": "-") + ", " + classIntMap + ')';
  }
}