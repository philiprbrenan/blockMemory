//------------------------------------------------------------------------------
// Btree using block memory, Written July 1-12, 2025
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2025
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Btree in a block on the surface of a silicon chip.

import java.util.*;

class Btree extends Test                                                        // Manipulate a btree in a block of memory
 {final int size;                                                               // The maximum number of nodes == branches or leaves in the btree
  final int maxStuckSize;                                                       // The maximum number of entries in the stuck.
  final int bitsPerKey;                                                         // The number of bits needed to define a key
  final int bitsPerData;                                                        // The number of bits needed to define a data field
  final Layout L;                                                               // Layout of the stuck
  final Layout.Field freeStart;                                                 // Start of free chain. Initially all stucks are on the free chain except the root stuck
  final Layout.Field stuckIsLeaf;                                               // Whether the current stuck is acting as a leaf or a branch in the btree.
  final Layout.Field stuckIsFree;                                               // Whether the stuck is on the free chain
  final Layout.Field freeNext;                                                  // Next stuck on the free chain. If this stuck is not on the free chain then this field is zero to show that this stuck in use
  final Layout.Field stuckSize;                                                 // Current size of stuck up to the maximum size
  final Layout.Field stuckKeys;                                                 // Keys field
  final Layout.Field stuckData;                                                 // Data field
  boolean supressMerge = false;                                                 // Supress merges during put to allow merge steps to be tested individually.  If this is on the trees built for testing are already merged so there is nothing to test.
  static boolean debug = false;                                                 // Debug if enabled

//D1 Construction                                                               // Construct and layout a btree

  Btree(int Size, int MaxStuckSize, int BitsPerKey, int BitsPerData)            // Create the Btree
   {if (MaxStuckSize % 2 == 1) stop("The stuck size must be even, not:", MaxStuckSize);
    if (MaxStuckSize < 4)      stop("The stuck size must be greater than equal to 4, not:", MaxStuckSize);
    size         = Size;                                                        // The maximum number of entries in the btree.
    maxStuckSize = MaxStuckSize;                                                // The maximum number of entries in the stuck.
    bitsPerKey   = BitsPerKey;                                                  // The number of bits needed to define a key
    bitsPerData  = BitsPerData;                                                 // The number of bits needed to define a data field
    L            = layout();                                                    // Layout of the btree
    freeStart    = L.locateFieldByName("freeStart");                            // Start of free chain. Initially all sticks are on the free chain except the root stuck
    stuckIsLeaf  = L.locateFieldByName("stuckIsLeaf");                          // Whether the stuck is a leaf
    stuckIsFree  = L.locateFieldByName("stuckIsFree");                          // Whether the stuck is on the free chain
    freeNext     = L.locateFieldByName("freeNext");                             // Next element refernce on free chain
    stuckSize    = L.locateFieldByName("stuckSize");                            // Current size of stuck up to the maximum size
    stuckKeys    = L.locateFieldByName("stuckKeys");                            // Keys field
    stuckData    = L.locateFieldByName("stuckData");                            // Data field

    iCreateFreeChain();                                                         // Create the free chain
   }

  Layout layout()                                                               // Layout describing Btree.
   {if (logTwo(size) >= bitsPerData)                                            // The data field must be big enought to act as a node pointer in branches and a data pointer in leaves to locate the actual dfata held else where
     {stop("Bits per data too small for tree of this size");
     }
    return new Layout(String.format("""
freeStart      var    %d
stucks         array  %d
  stuckIsLeaf  bit
  stuckIsFree  bit
  freeNext     var    %d
  stuckSize    var    %d
  stuck        array  %d
    stuckKeys  var    %d
    stuckData  var    %d
""", logTwo(size)+1, size, logTwo(size)+1, logTwo(maxStuckSize)+1, maxStuckSize, bitsPerKey, bitsPerData));
   }

  Layout.Field variable(String name, int size)                                  // Create a variable
   {final Layout.Field v = L.variable(name, size);
    v.layout.P = L.P;
    return v;
   }

  Layout.Field index()        {return variable("index", logTwo(size)+1);}       // Create an index for a stuck in a btree
  Layout.Field isLeaf()       {return variable("isLeaf",             1);}       // Create a bit for is leaf
  Layout.Field isFull()       {return variable("isFull",             1);}       // Create a bit for is full
  Layout.Field isFullButOne() {return variable("isFullButOne",       1);}       // Create a bit for is full but one
  Layout.Field within()       {return variable("within",             1);}       // Create a bit for is within the body of the stuck and not at the top
  Layout.Field found()        {return variable("found",              1);}       // Create a bit for whether a key has been found
  Layout.Field leafFull()     {return variable("leafFull",           1);}       // Create a bit for a leaf is full
  Layout.Field branchFull()   {return variable("branchFull",         1);}       // Create a bit for a branch is full

  void runProgram()                      {L.runProgram();}
  void clearProgram()                    {L.clearProgram();}
  void stopProgram(String message)       {L.stopProgram(message);}
  Layout.Program startNewProgram()       {return L.startNewProgram();}
  void continueProgram(Layout.Program p) {L.continueProgram(p);}

//D2 Allocation                                                                 // Allocate stucks from the free chain

  void createFreeChain()                                                        // Create the free chain
   {final Layout.Field index = index();
    for (int i = 1; i < size; i++)
     {freeStart.write(1);
      index.write(i);
      freeNext.write(i+1 == size ? 0 : i+1);
      stuckIsFree.write(1);
      freeNext   .write(index);
      stuckIsFree.write(index);
      setRootAsLeaf();
     }
   }

  void iCreateFreeChain()                                                       // Create the free chain
   {final Layout.Program p = L.startNewProgram();
    L.P.new Instruction()
     {void action()
       {createFreeChain();
       }
     };
    L.runProgram();
    L.continueProgram(p);
   }

  private void allocate(Layout.Field ref, boolean leaf)                         // Allocate a stuck and set a ref to the allocated node
   {final Layout.Field index = index();
    if (freeStart.value == 0)                                                   // Check memory
     {stopProgram("Out of memory");
      return;
     }
    index.move(freeStart);                                                      // Head of free chain gives allocated stuck
    ref  .move(freeStart);                     ;                                // Head of free chain gives allocated stuck
    stuckIsFree.zero(index);                                                    // Show as in use
    freeNext .read(index);                                                      // Locate next stuck on free chain to become new first stuck on free chain
    freeStart.move(freeNext);                                                   // Next stuck on free chain becomes head of free chain
    freeNext .zero(index);                                                      // Clear the next field from the current stuck
    if (leaf) setLeaf(ref); else setBranch(ref);
   }

  private void iAllocate(Layout.Field ref, boolean leaf)                        // Allocate a stuck and set a ref to the allocated node
   {L.P.new Instruction()
     {void action()
       {allocate(ref, leaf);
       }
     };
   }

  private void  allocateLeaf  (Layout.Field ref) { allocate(ref, true);}        // Allocate a stuck, set a ref to the allocated node and mark it a leaf
  private void  allocateBranch(Layout.Field ref) { allocate(ref, false);}       // Allocate a stuck, set a ref to the allocated node and mark it a branch

  private void iAllocateLeaf  (Layout.Field ref) {iAllocate(ref, true);}        // Allocate a stuck, set a ref to the allocated node and mark it a leaf
  private void iAllocateBranch(Layout.Field ref) {iAllocate(ref, false);}       // Allocate a stuck, set a ref to the allocated node and mark it a branch

  private void free(Layout.Field ref)                                           // Free the indicated stuck to make it available for reuse
   {if (ref.value == 0)                                                         // The root stuck cannot be freed
     {stopProgram("Cannot free the root stuck");
      return;
     }
    freeNext   .move(freeStart);
    freeNext   .write(ref);                                                     // Append the free chain to this stuck
    freeStart  .move (ref);                                                     // This stuck becomes the first stuick on the free chain
    stuckIsFree.one  (ref);                                                     // Show as free
   }

  private void iFree(Layout.Field ref)                                          // Free the indicated stuck to make it available for reuse
   {L.P.new Instruction()
     {void action()
       {free(ref);
       }
     };
   }

//D2 Stuck                                                                      // Get and set stucks within btree

  Stuck stuck()                                                                 // Make a temporary stuck we can copy into or out of as needed
   {final Stuck s = new Stuck(maxStuckSize, bitsPerKey, bitsPerData);
    s.L.P = L.P;
    return s;
   }

  void copyStuckFrom(Stuck S, Layout.Field BtreeIndex)                          // Copy a stuck out of the btree
   {final Layout.Field stuckIndex = S.index();

    stuckSize  .read(BtreeIndex);
    S.stuckSize.move(stuckSize);

    for (int i = 0; i < S.maxStuckSize; i++)
     {stuckIndex.write(i);
      stuckKeys.read(BtreeIndex, stuckIndex); S.stuckKeys.move(stuckKeys); S.stuckKeys.write(stuckIndex);
      stuckData.read(BtreeIndex, stuckIndex); S.stuckData.move(stuckData); S.stuckData.write(stuckIndex);
     }
   }

  void iCopyStuckFrom(Stuck S, Layout.Field BtreeIndex)                         // Copy a stuck out of the btree
   {L.P.new Instruction()
     {void action()
       {copyStuckFrom(S, BtreeIndex);
       }
     };
   }

  void saveStuckInto(Stuck S, Layout.Field BtreeIndex)                          // Save a stuck into the indicated position in the btree
   {final Layout.Field stuckIndex = S.index();

    stuckSize.move(S.stuckSize);                                                // Get the size field from the btree
    stuckSize.write(BtreeIndex);                                                // Set the size field in the stuck

    for (int i = 0; i < S.maxStuckSize; i++)
     {stuckIndex.write(i);
      S.stuckKeys.read(stuckIndex); stuckKeys.move(S.stuckKeys); stuckKeys.write(BtreeIndex, stuckIndex);
      S.stuckData.read(stuckIndex); stuckData.move(S.stuckData); stuckData.write(BtreeIndex, stuckIndex);
     }
   }

  void iSaveStuckInto(Stuck S, Layout.Field BtreeIndex)                         // Save a stuck into the indicated position in the btree
   {L.P.new Instruction()
     {void action()
       {saveStuckInto(S, BtreeIndex);                                           // Save a stuck into the indicated position in the btree
       }
     };
   }

  void copyStuckFromRoot(Stuck S)                                               // Copy a stuck out of the root of the btree
   {final Layout.Field i = index();
    i.zero();
    copyStuckFrom(S, i);
   }

  void iCopyStuckFromRoot(Stuck S)                                              // Copy a stuck out of the root of the btree
   {L.P.new Instruction()
     {void action()
       {copyStuckFromRoot(S);
       }
     };
   }

  void saveStuckIntoRoot(Stuck S)                                               // Copy a stuck out of the root of the btree
   {final Layout.Field i = index();
    i.zero();
    saveStuckInto(S, i);
   }

  void iSaveStuckIntoRoot(Stuck S)                                              // Copy a stuck out of the root of the btree
   {L.P.new Instruction()
     {void action()
       {saveStuckIntoRoot(S);
       }
    };
   }

//D1 Attributes                                                                 // Get and set attributes

  void setRootAsLeaf()                                                          // Set the root to be a leaf
   {final Layout.Field i = index();
    i.zero();
    stuckIsLeaf.one(i);
   }

  void iSetRootAsLeaf()                                                         // Set the root to be a leaf
   {final Layout.Field i = index();
    i.iZero();
    stuckIsLeaf.iOne(i);
   }

  void setRootAsBranch()                                                        // Set the root to be a branch
   {final Layout.Field i = index();
    i.zero();
    stuckIsLeaf.zero(i);
   }

  void iSetRootAsBranch()                                                       // Set the root to be a branch
   {final Layout.Field i = index();
    i.iZero();
    stuckIsLeaf.iZero(i);
   }

  void setLeaf  (Layout.Field i)  {stuckIsLeaf.one (i);}                        // Set a stuck in the btree to be a leaf
  void setBranch(Layout.Field i)  {stuckIsLeaf.zero(i);}                        // Set a stuck in the btree to be a branch

  void iSetLeaf  (Layout.Field i) {stuckIsLeaf.iOne (i);}                       // Set a stuck in the btree to be a leaf
  void iSetBranch(Layout.Field i) {stuckIsLeaf.iZero(i);}                       // Set a stuck in the btree to be a branch

  void isLeaf(Layout.Field index, Layout.Field isLeaf)                          // Is leaf at indicated index
   {stuckIsLeaf.read(index);
    isLeaf.move(stuckIsLeaf);
   }

  void iIsLeaf(Layout.Field index, Layout.Field isLeaf)                         // Is leaf at indicated index
   {L.P.new Instruction()
     {void action()
       {isLeaf(index, isLeaf);
       }
     };
   }

  void isRootLeaf(Layout.Field isLeaf)                                          // Is the root a leaf?
   {L.P.new Instruction()
     {void action()
       {final Layout.Field i = index();
        i.zero();
        stuckIsLeaf.read(i);
        isLeaf.move(stuckIsLeaf);
       }
     };
   }

  void isRootLeafFull(Layout.Field isFull)                                      // Is the root assumed to be a leaf full?
   {L.P.new Instruction()
     {void action()
       {final Layout.Field i = index();
        i.zero();
        stuckSize.read(i);
        isFull.value = stuckSize.value >= maxStuckSize ? 1 : 0;
       }
     };
   }

  void iIsRootBranchFull(Layout.Field isFullButOne)                             // Is the root assumed to be a root full?
   {L.P.new Instruction()
     {void action()
       {final Layout.Field i = index();
        i.zero();
        stuckSize.read(i);
        isFullButOne.value = stuckSize.value >= maxStuckSize-1 ? 1 : 0;
       }
     };
   }

//D1 Print                                                                      // Print the tree

  class DumpStuck                                                               // Dump the stuck at the specified index
   {final int index;
    final int size;
    final int next;
    final boolean leaf;
    final boolean free;
    final Stack<Integer>keys = new Stack<>();
    final Stack<Integer>data = new Stack<>();
    final Integer top;
    final Stuck stuck = stuck();

    DumpStuck(int Index)
     {index = Index;
      Layout.Field at = index(); at.value = index;

      final Layout.Program p = L.startNewProgram();
      stuckIsLeaf.iRead(at);
      stuckIsFree.iRead(at);
      freeNext   .iRead(at);
      iCopyStuckFrom(stuck, at);
      L.runProgram();
      L.continueProgram(p);

      leaf = stuckIsLeaf.value > 0;

      free = stuckIsFree.value > 0;
      next = freeNext.value;
      size = stuck.stuckSize.value;

      for (int i = 0; i < size; i++)
       {final Layout.Program P = L.startNewProgram();
        stuck.stuckKeys.iRead(i);
        stuck.stuckData.iRead(i);
        L.runProgram();
        L.continueProgram(P);
        keys.push(stuck.stuckKeys.value);
        data.push(stuck.stuckData.value);
       }
      if (!leaf)
       {final Layout.Program P = L.startNewProgram();
        stuck.stuckData.iRead(size);
        L.runProgram();
        L.continueProgram(P);
        top = stuck.stuckData.value;
       }
      else top = null;
     }

    public String toString()
     {final StringBuilder s = new StringBuilder();
      s.append( "index: "+index);
      s.append("  size: "+size);
      s.append("  leaf: "+leaf);
      s.append("  free: "+free);
      s.append("  keys: "+keys);
      s.append("  data: "+data);
      if (top != null) s.append("  top: "+top);
      return ""+s;
     }
   }

  public String toString() {return print();}                                    // Print tree

  String dump()
   {final StringBuilder s = new StringBuilder();
    final Stuck t = stuck();
    final Layout.Field stuckIndex = t.index();
    s.append("Btree\n");
    for (int i = 0; i < size; i++)                                              // Each stuck in the btree
     {final DumpStuck d = new DumpStuck(i);                                     // Load stuck description
      if (d.free) continue;                                                     // Not in use as it is on the free chain

      s.append(String.format("Stuck: %2d   size: %d   free: %d   next: %2d  leaf: %d\n",
        i, d.size, d.free ? 1 : 0, d.next, d.leaf ? 1 : 0));
      s.append(""+d.stuck);
     }
    return ""+s;
   }

//D2 Horizontally                                                               // Print the tree horizontally

    final int linesToPrintABranch =  4;                                         // The number of lines required to print a branch
    final int maxPrintLevels      = 10;                                         // The maximum nu ber of levels to print =- this avoids endless print loops when something goes wrong

    void printLeaf(int BtreeIndex, Stack<StringBuilder>P, int level)            // Print leaf horizontally
     {padStrings(P, level);
      final DumpStuck     S = new DumpStuck(BtreeIndex);

      final StringBuilder s = new StringBuilder();                              // String builder
      for  (int i = 0; i < S.size; i++)
       {s.append(""+S.keys.elementAt(i)+",");
       }
      if (s.length() > 0) s.setLength(s.length()-1);                            // Remove trailing comma if present
      s.append("="+BtreeIndex+" ");
      P.elementAt(level*linesToPrintABranch).append(s.toString());
      padStrings(P, level);
     }

    void printBranch(int BtreeIndex, Stack<StringBuilder>P, int level)          // Print branch horizontally
     {if (level > maxPrintLevels) return;
      padStrings(P, level);
      final DumpStuck S = new DumpStuck(BtreeIndex);
      final int       L = level * linesToPrintABranch;                          // Start line at which to print branch
      final int       K = S.size;                                               // Size of branch

      if (K > 0)                                                                // Branch has key, next pairs
       {for  (int i = 0; i < K; i++)
         {final int key  = S.keys.elementAt(i);
          final int data = S.data.elementAt(i);
          final DumpStuck C = new DumpStuck(data);
          if (C.leaf)
           {printLeaf  (data, P, level+1);
           }
          else
           {printBranch(data, P, level+1);
           }

          P.elementAt(L+0).append(""+S.keys.elementAt(i));                      // Key
          P.elementAt(L+1).append(""+BtreeIndex+(i > 0 ?  "."+i : ""));         // Branch,key, next pair
          P.elementAt(L+2).append(""+S.data.elementAt(i));
         }
       }
      else                                                                      // Branch is empty so print just the index of the branch
       {P.elementAt(L+0).append(""+BtreeIndex+"Empty");
       }
      final int top = S.top;                                                    // Top next will always be present
      P.elementAt(L+3).append(top);                                             // Append top next

      final DumpStuck T = new DumpStuck(top);
      if (T.leaf)                                                               // Print leaf
       {printLeaf  (top, P, level+1);
       }
      else                                                                      // Print branch
       {printBranch(top, P, level+1);
       }

      padStrings(P, level);
     }

   String printBoxed()                                                          // Print a tree in a box
    {final String  s = toString();
     final int     n = longestLine(s)-1;
     final String[]L = s.split("\n");
     final StringBuilder t = new StringBuilder();
     t.append("+"); t.append("-".repeat(n)); t.append("+\n");
     for(String l : L) t.append("| "+l+"\n");
     t.append("+"); t.append("-".repeat(n)); t.append("+\n");
     return t.toString();
    }

  void padStrings(Stack<StringBuilder> S, int level)                            // Pad the strings at each level of the tree so we have a vertical face to continue with - a bit like Marc Brunel's tunneling shield
   {final int N = level * linesToPrintABranch + maxStuckSize;                   // Number of lines we might want
    for (int i = S.size(); i <= N; ++i) S.push(new StringBuilder());            // Make sure we have a full deck of strings
    int m = 0;                                                                  // Maximum length
    for (StringBuilder s : S) m = m < s.length() ? s.length() : m;              // Find maximum length
    for (StringBuilder s : S)                                                   // Pad each string to maximum length
     {if (s.length() < m) s.append(" ".repeat(m - s.length()));                 // Pad string to maximum length
     }
   }

  String printCollapsed(Stack<StringBuilder> S)                                 // Collapse horizontal representation into a string
   {z();
    final StringBuilder t = new StringBuilder();                                // Print the lines of the tree that are not blank
    for  (StringBuilder s : S)
     {z();
      final String l = s.toString();
      if (l.isBlank()) continue;
      t.append(l+"|\n");
     }
    return t.toString();
   }

  String print()                                                                // Print a tree horizontally
   {final Stack<StringBuilder> P = new Stack<>();
    final DumpStuck d = new DumpStuck(0);
    if (d.leaf) printLeaf(0, P, 0); else printBranch(0, P, 0);
    return printCollapsed(P);
   }

//D1 Split                                                                      // Split nodes in half to increase the number of nodes in the tree

  private void splitRootLeaf()                                                  // Split a full root leaf
   {final Stuck p = stuck(), l = stuck(), r = stuck();                          // Parent == root, left, right stucks
    final Layout.Field isFull = isFull();
    final Layout.Field cl = index(), cr = index();                              // Indexes of left and right children
    final Layout.Field pl = p.key(), pr = p.key(), plr = p.key();               // Parent key must be smaller than anything in right child yet greater than or equal to anything in the left child

    copyStuckFromRoot(p);                                                       // Load leaf root stuck from btree
    p.isFull(isFull);                                                           // Check whether the leaf root stuck is full
    if (!isFull.asBoolean())
     {L.P.stopProgram("A root leaf must be full before it can be split");
     }
    p.splitIntoTwo(l, r, maxStuckSize / 2);                                     // Split the leaf root in two down the middle
    allocateLeaf(cl); saveStuckInto(l, cl);                                     // Allocate and save left leaf
    allocateLeaf(cr); saveStuckInto(r, cr);                                     // Allocate and save right leaf

    l.lastElement();  pl.move(l.stuckKeys);                                     // Last element of left child
    r.firstElement(); pr.move(r.stuckKeys);                                     // First element of right child
    plr.value = (pl.value + pr.value) / 2;                                      // Mid point key
    p.clear();                                                                  // Clear the root so we can add the left and right children to it.
                                                                                // Update root with new children
    p.stuckKeys.move(plr); p.stuckData.move(cl); p.push();                      // Add reference to left child
    p.stuckData.move(cr);  p.setPastLastElement();                              // Add reference to right child
    saveStuckIntoRoot(p);  setRootAsBranch();                                   // Save the root stuck back into the btree and mark it as a branch
   }

  private void iSplitRootLeaf()                                                 // Split a full root leaf
   {L.P.new Instruction()
     {void action()
       {splitRootLeaf();
       }
     };
   }

  private void splitRootBranch()                                                // Split a full root branch
   {final Stuck p = stuck(), l = stuck(), r = stuck();                          // Parent == root, left, right stucks
    final Layout.Field isFullButOne = isFullButOne();
    final Layout.Field           cl = index(), cr = index();                    // Indexes of left and right children
    final int              midPoint = (maxStuckSize-1) / 2;                     // Mid point in parent

    copyStuckFromRoot(p);                                                       // Load branch root stuck from btree

    p.isFullButOne(isFullButOne);                                               // Check whether the branch root stuck is full
    if (!isFullButOne.asBoolean())
     {L.P.stopProgram("A root branch must be full before it can be split");
     }

    p.splitIntoThree(l, r, midPoint);                                           // Split the branch root in two down the middle
    allocateBranch(cl); saveStuckInto(l, cl);                                   // Allocate and save left branch
    allocateBranch(cr); saveStuckInto(r, cr);                                   // Allocate and save right branch
                                                                                // Update root with new children
    p.stuckKeys.read(midPoint);                                                 // Get splitting key
    p.stuckData.move(cl);                                                       // Refence to left child stuck

    p.clear();                                                                  // Clear the root so we can add the left and right children to it.
    p.push();                                                                   // Add reference to left child
    p.stuckData.move(cr); p.setPastLastElement();                               // Add reference to right child as top element past the end of the stuck
    saveStuckIntoRoot(p);                                                       // Save the root stuck back into the btree and mark it as a branch
   }

  private void iSplitRootBranch()                                               // Split a full root branch
   {L.P.new Instruction()
     {void action()
       {splitRootBranch();
       }
     };
   }

  private void splitLeafNotTop                                                  // Split a full leaf that is not the root and is not the last child of its parent branch which is not full
   (Layout.Field parentIndex, Layout.Field stuckIndex)
   {final Stuck p = stuck(), c = stuck(), l = stuck();                          // Parent which must be a branch which is not full, child at index which must be a full leaf, left and right splits of leaf
    final Layout.Field isFull = isFull(), isFullButOne = isFullButOne();
    final Layout.Field isLeaf = isLeaf();
    final Layout.Field cl = index(), cr = index();                              // Btree indexes of child and left and right children of child
    final Layout.Field ck = p.key(), pl = p.key(), pr = p.key(), plr = p.key(); // Key of child in parent, splitting key which must be smaller than anything in right child of child yet greater than or equal to anything in the left child of child

    copyStuckFrom(p, parentIndex);                                              // Load parent stuck from btree
    p.stuckKeys.read(stuckIndex); ck.move(p.stuckKeys);                         // Key of child
    p.stuckData.read(stuckIndex); cr.move(p.stuckData);                         // Reference to child
    copyStuckFrom(c, p.stuckData);                                              // Load child
    isLeaf(parentIndex, isLeaf);                                                // The parent stuck must be a branch

    if (isLeaf.asBoolean())
     {L.P.stopProgram("Parent must be a branch");
     };
    p.isFullButOne(isFullButOne);                                               // The parent stuck may not be full

    if (isFullButOne.asBoolean())
     {L.P.stopProgram("Parent must not be full");
     }
    isLeaf(cr, isLeaf);                                                         // The child stuck must be a leaf

    if (!isLeaf.asBoolean())
     {L.P.stopProgram("Child must be a leaf");
     }

    c.isFull(isFull);                                                           // The child leaf stuck must be full
    if (!isFull.asBoolean())
     {L.P.stopProgram("Child leaf must be full");
     }

    c.splitLow(l, maxStuckSize / 2);                                            // Split the leaf in two down the middle copying out the lower half
    allocateLeaf(cl); saveStuckInto(l, cl);                                     // Allocate and save left leaf
                      saveStuckInto(c, cr);                                     // Allocate and save left leaf

    l.lastElement();  pl.move(l.stuckKeys);                                     // Last element of left child
    c.firstElement(); pr.move(c.stuckKeys);                                     // First element of right child
    plr.value = (pl.value + pr.value) / 2;                                      // Mid point key
    p.stuckKeys.move(plr); p.stuckData.move(cl);
    p.insertElementAt(stuckIndex);                                              // Add reference to left child
    saveStuckInto(p, parentIndex);                                              // Save the parent stuck back into the btree
   }

  private void iSplitLeafNotTop                                                 // Split a full leaf that is not the root and is not the last child of its parent branch which is not full
   (Layout.Field parentIndex, Layout.Field stuckIndex)
   {L.P.new Instruction()
     {void action()
       {splitLeafNotTop(parentIndex, stuckIndex);
       }
     };
   }

  private void splitLeafAtTop(Layout.Field parentIndex)                         // Split a full leaf that is not the root and is the last child of its parent branch which is not full
   {final Stuck p = stuck(), c = stuck(), l = stuck();                          // Parent which must be a branch which is not full, child at index which must be a full leaf, left and right splits of leaf
    final Layout.Field isFull       = isFull();
    final Layout.Field isFullButOne = isFullButOne();
    final Layout.Field isLeaf       = isLeaf();
    final Layout.Field cl = index(), cr = index();                              // Btree indexes of child and left and right children of child
    final Layout.Field pl = p.key(), pr = p.key(), plr = p.key();               // Key of child in parent, splitting key which must be smaller than anything in right child of child yet greater than or equal to anything in the left child of child

    copyStuckFrom(p, parentIndex);                                              // Load parent stuck from btree
    p.pastLastElement();                                                        // Key of child
    cr.move(p.stuckData);                                                       // Reference to child in btree
    copyStuckFrom(c, p.stuckData);                                              // Load child from btree

    isLeaf(parentIndex, isLeaf);                                                // The parent stuck must be a branch
    if (isLeaf.asBoolean())
     {L.P.stopProgram("Parent must be a branch");
     }

    p.isFullButOne(isFullButOne);                                               // The parent stuck may not be full
    if (isFullButOne.asBoolean())
     {L.P.stopProgram("Parent must not be full");
     }
                                                                                // Update root with new children
    isLeaf(cr, isLeaf);                                                         // The child stuck must be a leaf
    if (!isLeaf.asBoolean())
     {L.P.stopProgram("Child must be a leaf");
     }

    c.isFull(isFull);                                                           // The child stuck must be a leaf
    if (!isFull.asBoolean())
     {L.P.stopProgram("Child leaf must be full");
     }

    c.splitLow(l, maxStuckSize / 2);                                            // Split the leaf in two down the middle copying out the lower half
    allocateLeaf(cl); saveStuckInto(l, cl);                                     // Allocate and save left leaf
                      saveStuckInto(c, cr);                                     // Allocate and save left leaf


    l.lastElement();  pl.move(l.stuckKeys);                                     // Last element of left child
    c.firstElement(); pr.move(c.stuckKeys);                                     // First element of right child
    plr.value = (pl.value + pr.value) / 2;                                      // Mid point key which is always greater than or equal to the left high key and less than the right low key
    p.stuckKeys.move(plr); p.stuckData.move(cl); p.push();                      // Add reference to left child
    p.stuckKeys.zero();
    p.stuckData.move(cr);
    p.setPastLastElement();                                                     // Add reference to not split top child on the right
    saveStuckInto(p, parentIndex);                                              // Save the parent stuck back into the btree
   }

  private void iSplitLeafAtTop(Layout.Field parentIndex)                        // Split a full leaf that is not the root and is the last child of its parent branch which is not full
   {L.P.new Instruction()
     {void action()                                                             // Compute mid point key
       {splitLeafAtTop(parentIndex);
       }
     };
   }

  private void splitBranchNotTop                                                // Split a full branch that is not the root and is not the last child of its parent branch which is not full
   (Layout.Field parentIndex, Layout.Field stuckIndex)
   {final Stuck p = stuck(), c = stuck(), l = stuck(), r = stuck();             // Parent which must be a branch which is not full, child at index which must be a full leaf, left and right splits of leaf
    final Layout.Field isFullButOne = isFullButOne();
    final Layout.Field isLeaf       = isLeaf();
    final Layout.Field cl           = index(), cr = index();                    // Btree indexes of child and left and right children of child
    final Layout.Field ck           = p.key();                                  // Key of child in parent, splitting key which must be smaller than anything in right child of child yet greater than or equal to anything in the left child of child
    final Layout.Field key          = p.key();                                  // The central key

    copyStuckFrom(p, parentIndex);                                              // Load parent stuck from btree
    p.stuckKeys.read(stuckIndex); ck.move(p.stuckKeys);                         // Key of child
    p.stuckData.read(stuckIndex); cr.move(p.stuckData);                         // Reference to child
    copyStuckFrom(c, p.stuckData);                                              // Load child

    isLeaf(parentIndex, isLeaf);                                                // The parent stuck must be a branch
    if (isLeaf.asBoolean())
     {L.P.stopProgram("Parent must be a branch");
     }

    p.isFullButOne(isFullButOne);                                               // The parent stuck may not be full
    if (isFullButOne.asBoolean())
     {L.P.stopProgram("Parent must not be full");
     }

    isLeaf(cr, isLeaf);                                                         // The child stuck must be a branch
    if (isLeaf.asBoolean())
     {L.P.stopProgram("Child must be a branch");
     }

    c.isFullButOne(isFullButOne);                                               // The child stuck must be a leaf
    if (!isFullButOne.asBoolean())
     {L.P.stopProgram("Child branch must be full");
     }

    c.splitLowButOne(l, (maxStuckSize-1) / 2, key);                             // Split the leaf in two down the middle copying out the lower half

    allocateBranch(cl); saveStuckInto(l, cl);                                   // Allocate and save left leaf
                        saveStuckInto(c, cr);                                   // Allocate and save left leaf
                                                                                // Update root with new children
    p.stuckKeys.move(key); p.stuckData.move(cl);
    p.insertElementAt(stuckIndex);                                              // Add reference to left child
    saveStuckInto(p, parentIndex);                                              // Save the parent stuck back into the btree
   }

  private void iSplitBranchNotTop                                               // Split a full leaf that is not the root and is the last child of its parent branch which is not full
   (Layout.Field parentIndex, Layout.Field stuckIndex)
   {L.P.new Instruction()
     {void action()                                                             // Compute mid point key
       {splitBranchNotTop(parentIndex, stuckIndex);
       }
     };
   }

  private void splitBranchAtTop(Layout.Field parentIndex)                       // Split a full branch that is not the root and is the last child of its parent branch which is not full
   {final Stuck p = stuck(), c = stuck(), l = stuck();                          // Parent which must be a branch which is not full, child at index which must be a full leaf, left and right splits of leaf
    final Layout.Field isFullButOne = isFullButOne();
    final Layout.Field isLeaf       = isLeaf();
    final Layout.Field cl           = index(), cr = index();                    // Btree indexes of child and left and right children of child
    final Layout.Field center       = p.key();                                  // The central key

    copyStuckFrom(p, parentIndex);                                              // Load parent stuck from btree
    p.pastLastElement();                                                        // Key of child
    cr.move(p.stuckData);                                                       // Reference to child in btree
    copyStuckFrom(c, p.stuckData);                                              // Load child from btree

    isLeaf(parentIndex, isLeaf);                                                // The parent stuck must be a branch
    if (isLeaf.asBoolean())
     {L.P.stopProgram("Parent must be a branch");
     }

    p.isFullButOne(isFullButOne);                                               // The parent stuck may not be full
    if (isFullButOne.asBoolean())
     {L.P.stopProgram("Parent must not be full");
     }

    isLeaf(cr, isLeaf);                                                         // The child stuck must be a leaf
    if (isLeaf.asBoolean())
     {L.P.stopProgram("Child must be a branch");
     }

    c.isFullButOne(isFullButOne);                                               // The child stuck must be a leaf
    if (!isFullButOne.asBoolean())
     {L.P.stopProgram("Child branch must be full");
     }

    c.splitLowButOne(l, (maxStuckSize-1) / 2, center);                          // Split the leaf in two down the middle copying out the lower half
    allocateBranch(cl); saveStuckInto(l, cl);                                   // Allocate and save left leaf
                        saveStuckInto(c, cr);                                   // Allocate and save left leaf
                                                                                // Update root with new children
    p.stuckKeys.move(center); p.stuckData.move(cl); p.push();                   // Add reference to left child
    p.stuckKeys.zero();       p.stuckData.move(cr); p.setPastLastElement();     // Add reference to not split top child on the right
    saveStuckInto(p, parentIndex);                                              // Save the parent stuck back into the btree
   }

  private void iSplitBranchAtTop(Layout.Field parentIndex)                      // Split a full branch that is not the root and is the last child of its parent branch which is not full
   {L.P.new Instruction()
     {void action()                                                             // Compute mid point key
       {splitBranchAtTop(parentIndex);
       }
     };
   }

//D1 Merge                                                                      // Merge two nodes

  private void iMergeLeavesIntoRoot(Layout.Field success)                       // Merge two leaves into the root
   {final Stuck p = stuck(), l = stuck(), r = stuck();                          // Root and left, right children
    final Layout.Field li  = index(), ri = index();                             // Btree indexes of left and right children of root

    L.P.new Block()
     {void code()
       {L.P.new Instruction()                                                   // Check that the root has one entry and thus two children
         {void action()
           {success.zero();                                                     // Assume failure
            copyStuckFromRoot(p);                                               // Load root
            if (p.stuckSize.value != 1) L.P.Goto(end);                          // Wrong number of entries in root
           };
         };
        L.P.new Instruction()                                                   // Check that the root has one entry and thus two children
         {void action()
           {p.stuckData.read(0); li.move(p.stuckData);                          // Index of left leaf
            p.stuckData.read(1); ri.move(p.stuckData);                          // Index of right leaf
           };
         };
        new IsLeaf(li)                                                          // Check that the children are leaves
         {void Leaf()
           {L.P.new Instruction()                                               // Check that the root has one entry and thus two children
             {void action()
               {copyStuckFrom(l, li);                                           // Load left  leaf from btree
                copyStuckFrom(r, ri);                                           // Load right leaf from btree
                p.merge(l, r, success);                                         // Merge leaves into root
                if (success.asBoolean())                                        // Modify the root only if the merge succeeded
                 {saveStuckIntoRoot(p);                                         // Save the modified root back into the tree
                  setRootAsLeaf();                                              // Set the root to be a leaf
                  free(li); free(ri);                                           // Free left and right leaves as they are no longer needed
                 }
               }
             };
           }
         };
       }
     };
   }

  private void iMergeLeavesNotTop                                               // Merge the two consecutive leaves of a branch that is not the root. Neither of the leaves is the topmost leaf.
   (Layout.Field Parent, Layout.Field LeftLeaf, Layout.Field success)
   {final Stuck p = stuck(), l = stuck(), r  = stuck();                         // Parent, left and right children
    final Layout.Field li = index(), ri = index();                              // Btree indexes of left and right children of parent that we want to merge

    L.P.new Block()
     {void code()
       {L.P.new Instruction()                                                   // Check that the parent has a child at the specified index
         {void action()
           {success.zero();                                                     // Assume failure
            copyStuckFrom(p, Parent);                                           // Load parent
            if (p.stuckSize.value < 2) L.P.Goto(end);
           }
         };

        L.P.new Instruction()                                                   // Check that the parent has a child at the specified index
         {void action()
           {p.stuckData.read    (LeftLeaf); li.move(p.stuckData);               // Get the btree index of the left child leaf
            p.stuckData.readNext(LeftLeaf); ri.move(p.stuckData);               // Get the btree index of the right child leaf
           }
         };

        new IsLeaf(li)                                                          // Check that the children are leaves
         {void Leaf()
           {L.P.new Instruction()                                               // Check that the parent has a child at the specified index
             {void action()
               {copyStuckFrom(l, li);                                           // Load left  leaf from btree
                copyStuckFrom(r, ri);                                           // Load right leaf from btree
                l.merge(r, success);                                            // Merge leaves into left child

                if (success.asBoolean())                                        // Modify the parent only if the merge succeeded
                 {p.removeElementAt(LeftLeaf);                                  // Remove the left child
                  p.stuckData.move(li); p.setDataAt(LeftLeaf);                  // Replace the right child with the left child
                  saveStuckInto(l, li);                                         // Save the modified left child back into the tree
                  saveStuckInto(p, Parent);                                     // Save the modified root back into the tree
                  free(ri);                                                     // Free right leaf as it is no longer in use
                 }
               }
             };
           }
         };
       }
     };
   }

  private void iMergeLeavesAtTop(Layout.Field Parent, Layout.Field success)     // Merge the top most two leaves of a branch that is not the root
   {final Stuck p = stuck(), l = stuck(), r  = stuck();                         // Parent, left and right children
    final Layout.Field ls = p.index(),    rs = p.index();                       // Indices in stuck of left and right children
    final Layout.Field li = index(),      ri = index();                         // Btree indexes of left and right children of parent that we want to merge

    L.P.new Block()
     {void code()
       {L.P.new Instruction()
         {void action()
           {success.zero();                                                     // Assume failure
            copyStuckFrom(p, Parent);                                           // Load parent
            L.P.GoZero(end, p.stuckSize);                                       // Stuck must have at least one entry
           }
         };

        L.P.new Instruction()
         {void action()
           {ls.move(p.stuckSize); ls.dec();                                     // Index of left leaf known to be valid as the parent contains at least one entry resulting in two children
            rs.move(p.stuckSize);                                               // Index of right leaf
            p.stuckData.read(ls); li.move(p.stuckData);                         // Get the btree index of the left child leaf
            p.stuckData.read(rs); ri.move(p.stuckData);                         // Get the btree index of the right child leaf
           }
         };

        new IsLeaf(li)                                                          // Check that the children are leaves
         {void Leaf()                                                           // Children are leaves
           {L.P.new Instruction()
             {void action()
               {copyStuckFrom(l, li);                                           // Load left  leaf from btree
                copyStuckFrom(r, ri);                                           // Load right leaf from btree
                l.merge(r, success);                                            // Merge leaves into left child
                if (success.asBoolean())                                        // Modify the parent only if the merge succeeded
                 {p.stuckSize.dec();                                            // The left child is now topmost - we know this is ok because the parent has at elast one entry
                  saveStuckInto(l, li);                                         // Save the modified left child back into the tree
                  saveStuckInto(p, Parent);                                     // Save the modified root back into the tree
                  free(ri);                                                     // Free right leaf as it is no longer in use
                 }
               }
             };
           }
         };
       }
     };
   }

  private void iMergeBranchesIntoRoot(Layout.Field success)                     // Merge two branches into the root
   {final Stuck p = stuck(), l = stuck(),  r  = stuck();                        // Root and left, right children
    final Layout.Field li  = index(), ri = index();                             // Btree indexes of left and right children of root
    final Layout.Field k   = p.key();                                           // Splitting key

    iCopyStuckFromRoot(p);                                                      // Load root
    L.P.new Block()
     {void code()
       {L.P.new Instruction()                                                   // Check that the root has one entry and thus two children
         {void action()
           {success.zero();                                                     // Assume failure
            if (p.stuckSize.value != 1) L.P.Goto(end);
           };
         };

        L.P.new Instruction()                                                   // Check that the root has one entry and thus two children
         {void action()
           {p.stuckKeys.read(0); k .move(p.stuckKeys);                          // Splitting key
            p.stuckData.read(0); li.move(p.stuckData);                          // Index of left branch
            p.stuckData.read(1); ri.move(p.stuckData);                          // Index of right branch
           }
         };

        new IsLeaf(li)                                                          // Check that the children are leaves
         {void Branch()                                                         // Children are not leaves
           {L.P.new Instruction()
             {void action()
               {copyStuckFrom(l, li);                                           // Load left  branch from btree
                copyStuckFrom(r, ri);                                           // Load right branch from btree
                p.mergeButOne(l, k, r, success);                                // Merge left branch, splitting key, right branch into root
                if (success.asBoolean())                                        // Modify the parent only if the merge succeeded
                 {saveStuckIntoRoot(p);                                         // Save the modified root back into the tree
                  free(li); free(ri);                                           // Free left and right leaves as they are no longer needed
                 }
               }
             };
           }
         };
       }
     };
   }

  private void iMergeBranchesNotTop
   (Layout.Field Parent, Layout.Field LeftBranch, Layout.Field success)         // Merge the two consecutive child branches of a branch that is not the root. Neither of the child branches is the topmost leaf.
   {final Stuck p = stuck(), l = stuck(), r  = stuck();                         // Parent, left and right children
    final Layout.Field li = index(), ri = index();                              // Btree indexes of left and right children of parent that we want to merge

    L.P.new Block()
     {void code()
       {L.P.new Instruction()                                                   // Check that the parent has a child at the specified index
         {void action()
           {success.zero();                                                     // Assume failure
            copyStuckFrom(p, Parent);                                           // Load parent
            if (p.stuckSize.value < 2)
             {L.P.Goto(end);
             }
           };
         };

        L.P.new Instruction()                                                   // Check that the parent has a child at the specified index
         {void action()
           {p.stuckData.read    (LeftBranch); li.move(p.stuckData);             // Get the btree index of the left child branch
            p.stuckData.readNext(LeftBranch); ri.move(p.stuckData);             // Get the btree index of the right child branch
           }
         };

        new IsLeaf(li)                                                          // Check that the children are branches
         {void Branch()
           {L.P.new Instruction()                                               // Check that the parent has a child at the specified index
             {void action()
               {copyStuckFrom(l, li);                                           // Load left  branch from btree
                copyStuckFrom(r, ri);                                           // Load right branch from btree
                p.stuckKeys.read(LeftBranch);                                   // Key associated with left child branch
                l.mergeButOne(p.stuckKeys, r, success);                         // Merge branches into left child

                if (success.asBoolean())                                        // Modify the parent only if the merge succeeded
                 {p.removeElementAt(LeftBranch);                                // Remove the left child
                  p.stuckData.move(li); p.setDataAt(LeftBranch);                // Replace the right child with the left child
                  saveStuckInto(l, li);                                         // Save the modified left child back into the tree
                  saveStuckInto(p, Parent);                                     // Save the modified root back into the tree
                  free(ri);                                                     // Free right branch as it is no longer in use
                 }
               }
             };
           }
         };
       }
     };
   }

  private void iMergeBranchesAtTop(Layout.Field Parent, Layout.Field success)   // Merge the top most two child branches of a branch that is not the root
   {final Stuck p = stuck(), l = stuck(), r  = stuck();                         // Parent, left and right children
    final Layout.Field ls = p.index(),    rs = p.index();                       // Indices in stuck of left and right children
    final Layout.Field li = index(),      ri = index();                         // Btree indexes of left and right children of parent that we want to merge

    L.P.new Block()
     {void code()
       {L.P.new Instruction()                                                   // Check that the parent has a child at the specified index
         {void action()
           {success.zero();                                                     // Assume failure
            copyStuckFrom(p, Parent);                                           // Load parent

            if (p.stuckSize.value == 0)
             {L.P.Goto(end);
             }
           };
         };

        L.P.new Instruction()                                                   // Check that the parent has a child at the specified index
         {void action()
           {ls.move(p.stuckSize); ls.dec();                                     // Index of left branch known to be valid as the parent contains at least one entry resulting in two children
            rs.move(p.stuckSize);                                               // Index of right branch
            p.stuckData.read(ls); li.move(p.stuckData);                         // Get the btree index of the left branch branch
            p.stuckData.read(rs); ri.move(p.stuckData);                         // Get the btree index of the right branch branch
           }
         };

        new IsLeaf(li)                                                          // Check that the children are branches
         {void Branch()                                                         // Children are branches
           {L.P.new Instruction()                                               // Check that the parent has a child at the specified index
             {void action()
               {copyStuckFrom(l, li);                                           // Load left  branch from btree
                copyStuckFrom(r, ri);                                           // Load right branch from btree
                p.pop();                                                        // Key associated with left child branch
                l.mergeButOne(p.stuckKeys, r, success);                         // Merge leaves into left child
                if (success.asBoolean())                                        // Modify the parent only if the merge succeeded
                 {p.stuckData.move(li);                                         // Index of left branch that now contains the combined branches
                  p.setPastLastData();                                          // Make newly combined left branch top most
                  saveStuckInto(l, li);                                         // Save the modified left child back into the tree
                  saveStuckInto(p, Parent);                                     // Save the modified root back into the tree
                  free(ri);                                                     // Free right branch as it is no longer in use
                 }
               }
             };
           }
         };
       }
     };
   }

//D1 Find                                                                       // Find a key in a btree

  class IsLeaf                                                                  // Process a stuck depending on wnether it is a leaf or a branch
   {IsLeaf(Layout.Field index)
     {stuckIsLeaf.iRead(index);
      final IsLeaf l = this;
      L.P.new If(stuckIsLeaf)
       {void Then() {l.Leaf();}
        void Else() {l.Branch();}
       };
     }
    void Leaf() {}
    void Branch() {}
   }

  public void find(Layout.Field Key, Layout.Field Found,                        // Find the leaf associated with a key in the tree
    Layout.Field Data, Layout.Field index, Layout.Field stuckIndex)
   {final Stuck        S = stuck();
    final Layout.Field s = index;
    s.iZero();                                                                  // Start at the root
    L.P.new Block()
     {void code()
       {L.P.new Instruction()
         {void action()
           {copyStuckFrom(S, s);                                                // Set search key
            S.stuckKeys.move(Key);
           }
         };

        new IsLeaf(s)
         {void Leaf()                                                           // At a leaf - search for exact match
           {L.P.new Instruction()
             {void action()
               {S.search_eq(Found, stuckIndex);                                 // Search
                L.P.GoZero(end, Found);                                         // Key not present
               }
             };
            L.P.new Instruction()
             {void action()
               {S.elementAt(stuckIndex);                                        // Look up data
                Data.move(S.stuckData);                                         // Save data
                L.P.Goto (end);                                                 // Successfully found the key
               }
             };
           }
          void Branch()                                                         // On a branch - step to next level down
           {L.P.new Instruction()
             {void action()
               {S.search_le(Found, stuckIndex);                                 // Search stuck for matching key
                s.move(S.stuckData);                                            // Index of next stuck down
                L.P.Goto(start);                                                // Key not present
               }
             };
           }
         };
       };
     };
   }

//D1 Insertion                                                                  // Insert a key, data pair into the tree if ther is room for it or update and existing key with a new datum

  private void findAndInsert(Layout.Field Found)                                // Find the leaf that should contain this key and insert or update it is possible setting Found to true if found else to false indicating that the key, data pair still needs to be inserted
   {final Stuck  S          = stuck();
    Layout.Field Key        = S.key();
    Layout.Field Data       = S.data();
    Layout.Field index      = index();
    Layout.Field stuckIndex = S.index();
    Layout.Field full       = S.full();

    L.P.new Block()
     {void code()
       {L.P.new Instruction()
         {void action()
           {Key .move(stuckKeys);
            Data.move(stuckData);
           }
         };

        find(Key, Found, Data, index, stuckIndex);                              // Find the leaf that should contain the key and possibly the key.

        L.P.new Instruction()
         {void action()
           {copyStuckFrom(S, index);                                            // Copy the stuck that should contain the key
            S.stuckKeys.move(Key);
            S.stuckData.move(Data);
            if (Found.asBoolean())                                              // Found the key in the leaf so update it with the new data
             {S.setElementAt(stuckIndex);
              saveStuckInto(S, index);
              Found.one();
              L.P.Goto(end);
             }

            S.isFull(full);                                                     // Check whether the stuck is full
            if (!full.asBoolean())
             {S.search_le(Found, stuckIndex);
              S.stuckKeys.move(Key);
              S.stuckData.move(Data);
              if (Found.asBoolean()) S.insertElementAt(stuckIndex);
              else S.push();
              saveStuckInto(S, index);
              Found.one();
             }
            else Found.zero();                                                  // The key has not been inserted
           }
         };
       }
     };
   }

  public void put()                                                             // Insert a key, data pair into the tree or update and existing key with a new datum
   {final Stuck        S          = stuck();
    final Layout.Field p          = index();                                    // Previous or parent position in the btree
    final Layout.Field s          = index();                                    // Current position in the btree
    final Layout.Field Key        = S.key();
    final Layout.Field Data       = S.data();
    final Layout.Field index      = index();
    final Layout.Field stuckIndex = S.index();
    final Layout.Field full       = S.full();
    final Layout.Field found      = S.found();
    final Layout.Field isLeaf     = isLeaf();
    final Layout.Field fullButOne = S.fullButOne();

    L.P.new Instruction()
     {void action()
       {Key.move(stuckKeys); Data.move(stuckData);                              // Save key and data to be inserted,  It is convenient for the caller to be able to use the predeclared fields but theya re overwrittne by subsequqnt activity and so they have to ba saved immediately.
       }
     };

    L.P.new Block()                                                             // The block is left as soon as possible
     {void code()
       {L.P.new Instruction()
         {void action()
           {stuckKeys.move(Key);
            stuckData.move(Data);
           }
         };

        findAndInsert(found);                                                   // Try direct insertion with no modifications to the shape of the tree
        L.P.iGoNotZero(end, found);                                             // Direct insertion succeeded

        isRootLeaf(isLeaf);                                                     // Failed to insert because the root is a leaf and must therefore be full
        L.P.new If (isLeaf)                                                     // Root is a leaf
         {void Then()
           {L.P.new Instruction()
             {void action()
               {splitRootLeaf();                                                // Split the leaf root to make room
                stuckKeys.move(Key);                                            // Key, data pair to be inserted
                stuckData.move(Data);
               }
             };

            findAndInsert(found);                                               // Splitting a leaf root will make more space in the tree
            L.P.iGoto(end);                                                     // Direct insertion succeeded
           }
         };
        iIsRootBranchFull(fullButOne);                                          // Root is a full branch so split it
        L.P.new If (fullButOne)
         {void Then()
           {L.P.new Instruction()
             {void action()
               {splitRootBranch();                                              // Split the branch root to make room
                L.P.Goto(start);                                                // Restart descent to make sure we are on the right path
               }
             };
           }
         };

        L.P.new Instruction()
         {void action()
           {s.zero(); p.zero();                                                 // Start at the root and step down through the tree to the key splitting as we go
            copyStuckFrom(S, s);                                                // Load root
           }
         };

        L.P.new Block()
         {void code()
           {L.P.new Instruction()
             {void action()
               {S.stuckKeys.move(Key);
                S.search_le(found, stuckIndex);                                 // Step down
                p.move(s);                                                      // Parent
                s.move(S.stuckData);                                            // Child
                copyStuckFrom(S, s);                                            // Load child
               }
             };

            new IsLeaf(s)                                                       // Child is a leaf or a branch
             {void Leaf()                                                       // At a leaf - search for exact match
               {L.P.new Instruction()
                 {void action()
                   {S.isFull(full);

                    if (full.asBoolean())                                       // Split leaf
                     {if (found.asBoolean())
                       {splitLeafNotTop(p, stuckIndex);                         // Split the child leaf known not to be top
                       }
                      else
                       {splitLeafAtTop(p);                                      // Split the child leaf known to be top
                       }
                     }

                    stuckKeys.move(Key);                                        // Key, data pair to be inserted
                    stuckData.move(Data);
                   }
                 };

                findAndInsert(found);                                           // Must be insertable now necuase we have split everything in the path of the key
                L.P.iGoto(end);                                                 // Successfully found the key
               }
              void Branch()                                                     // Child is a branch
               {L.P.new Instruction()
                 {void action()
                   {S.isFullButOne(fullButOne);
                    if (fullButOne.asBoolean())
                     {if (found.asBoolean())
                       {splitBranchNotTop(p, stuckIndex);                       // Split the child branch known not to be top
                       }
                      else
                       {splitBranchAtTop(p);                                    // Split the child branch known to be top
                       }
                      s.move(p);                                                // Restart at the parent so we enter the child stuck that contains the key
                      copyStuckFrom(S, s);                                      // Reload stuck so we start again at the parent level
                     }
                    L.P.Goto(start);                                            // Try again
                   }
                 };
               }
             };
           };
         };
       }
     };
    if (!supressMerge)                                                          // Switch of combined merge during testing so that the individual merges can tested
     {stuckKeys.iMove(Key);                                                     // Key being inserted or updated
      merge();                                                                  // Merge along path to key
     }
   }

  public void merge()                                                           // Merge stucks on either side of the path to the key
   {final Stuck        S          = stuck();
    final Layout.Field s          = index();                                    // Current position in the btree
    final Layout.Field Key        = S.key();
    final Layout.Field stuckIndex = S.index();
    final Layout.Field found      = S.found();
    final Layout.Field within     = within();                                   // In the body of the stuck and not at the top
    final Layout.Field isLeaf     = isLeaf();
    final Layout.Field success    = S.success();

    L.P.new Block()                                                             // The block is left as soon as possible
     {void code()
       {L.P.new Instruction()
         {void action()
           {Key.move(stuckKeys);                                                // Save path key
            s.zero();                                                           // Start at the root and step down through the tree along the path of the key merging on each side of the key as we go
           }
         };

        new IsLeaf(s)                                                           // Root is a leaf or a branch
         {void Leaf()                                                           // Root is a leaf - nothing to merge
           {L.P.iGoto(end);
           }
         };

        iMergeLeavesIntoRoot(success);                                          // Try merging leaves into root
        L.P.iGoNotZero(end,  success);                                          // A successful merge makes the root a leaf so we can return

        iMergeBranchesIntoRoot(success);                                        // Try merging branches into root

        iCopyStuckFrom(S, s);                                                   // Load root

        L.P.new Block()
         {void code()
           {iMergeLeavesAtTop  (s, success);                                    // Try merging leaves at top into parent
            iMergeBranchesAtTop(s, success);                                    // Try merging branches at top into parent
            for (int i = 0; i < maxStuckSize-1; i++)
             {final int I = i;
              L.P.new Instruction()                                             // Check we are in the body of the stuck
               {void action()
                 {stuckIndex.write(I);
                  within.value = I < S.stuckSize.value ? 1 : 0;
                 }
               };
              L.P.new If(within)                                                // Within body of stuck
               {void Then()
                 {iMergeLeavesNotTop  (s, stuckIndex, success);                 // Try merging leaves not at top into parent
                  iMergeBranchesNotTop(s, stuckIndex, success);                 // Try merging branches not at top into parent
                 }
               };
             }

            L.P.new Instruction()
             {void action()
               {S.stuckKeys.move(Key);                                          // Following the path made by this key
                S.search_le(found, stuckIndex);                                 // Step down
                s.move(S.stuckData);                                            // Child
                copyStuckFrom(S, s);                                            // Load child
               }
             };

            new IsLeaf(s)                                                       // Child is a leaf or a branch
             {void Leaf()                                                       // At a leaf - end of merging
               {L.P.iGoto(end);
               }
              void Branch()                                                     // Child is a branch - try again
               {L.P.iGoto(start);
               }
             };
           };
         };
       }
     };
   }

//D1 Deletion                                                                   // Delete a key data pair from the btree returning the data associated with the key

  private void delete(Layout.Field Data)                                        // Find the leaf that contains this key and delete it
   {final Stuck  S          = stuck();
    Layout.Field Key        = S.key();
    Layout.Field index      = index();
    Layout.Field stuckIndex = S.index();
    Layout.Field found      = found();

    L.P.new Block()
     {void code()
       {Key.iMove(stuckKeys);
        find(Key, found, Data, index, stuckIndex);                              // Find the leaf that should contain the key and possibly the key.
        iCopyStuckFrom(S, index);                                               // Copy the stuck that should contain the key
        L.P.new If (found)                                                      // Found the key in the leaf so remove it
         {void Then()
           {L.P.new Instruction()
             {void action()
               {S.removeElementAt(stuckIndex);                                  // Remove the key
                saveStuckInto(S, index);                                        // Save modified stuck back into btree
                stuckKeys.move(Key);                                            // Reload key
               }
             };
            merge();                                                            // Merge along key path
           }
         };
       }
     };
   }

//D1 Tests                                                                      // Test the btree

  final static int[]random_100 = {27, 442, 545, 317, 511, 578, 391, 993, 858, 586, 472, 906, 658, 704, 882, 246, 261, 501, 354, 903, 854, 279, 526, 686, 987, 403, 401, 989, 650, 576, 436, 560, 806, 554, 422, 298, 425, 912, 503, 611, 135, 447, 344, 338, 39, 804, 976, 186, 234, 106, 667, 494, 690, 480, 288, 151, 773, 769, 260, 809, 438, 237, 516, 29, 376, 72, 946, 103, 961, 55, 358, 232, 229, 90, 155, 657, 681, 43, 907, 564, 377, 615, 612, 157, 922, 272, 490, 679, 830, 839, 437, 826, 577, 937, 884, 13, 96, 273, 1, 188};

  static Btree test_create()
   {final Btree b = new Btree(32, 4, 8, 8);
    ok(b.dump(), """
Btree
Stuck:  0   size: 0   free: 0   next:  0  leaf: 1
stuckSize: value=0
stuckKeys: value=0, 0=0, 1=0, 2=0, 3=0
stuckData: value=0, 0=0, 1=0, 2=0, 3=0
""");
    return b;
   }

  static void test_leaf()
   {final Btree b = test_create();
    final Layout.Field index = b.index();
    final Layout.Field a     = b.variable("a", 4);

    index.iWrite(0);
    b.new IsLeaf(index)
     {void Leaf  () {a.iWrite(1);}
      void Branch() {a.iWrite(2);}
     };
    b.runProgram();
    ok(a, "a: value=1");
   }

  static void test_allocFree()
   {final Btree b = test_create();
    final Layout.Field x = b.index();
    final Layout.Field y = b.index();

    ok(b.freeStart, "freeStart: value=1");
    ok(b.freeNext,  "freeNext: value=0, 0=0, 1=2, 2=3, 3=4, 4=5, 5=6, 6=7, 7=8, 8=9, 9=10, 10=11, 11=12, 12=13, 13=14, 14=15, 15=16, 16=17, 17=18, 18=19, 19=20, 20=21, 21=22, 22=23, 23=24, 24=25, 25=26, 26=27, 27=28, 28=29, 29=30, 30=31, 31=0");
    ok(b.dump(), """
Btree
Stuck:  0   size: 0   free: 0   next:  0  leaf: 1
stuckSize: value=0
stuckKeys: value=0, 0=0, 1=0, 2=0, 3=0
stuckData: value=0, 0=0, 1=0, 2=0, 3=0
""");

    b.iAllocate(x, false);
    b.iAllocate(y, false);
    b.runProgram();

    ok(x, "index: value=1");
    ok(y, "index: value=2");

    ok(b.freeStart, "freeStart: value=3");
    ok(b.freeNext,  "freeNext: value=0, 0=0, 1=0, 2=0, 3=4, 4=5, 5=6, 6=7, 7=8, 8=9, 9=10, 10=11, 11=12, 12=13, 13=14, 14=15, 15=16, 16=17, 17=18, 18=19, 19=20, 20=21, 21=22, 22=23, 23=24, 24=25, 25=26, 26=27, 27=28, 28=29, 29=30, 30=31, 31=0");

    ok(b.dump(), """
Btree
Stuck:  0   size: 0   free: 0   next:  0  leaf: 1
stuckSize: value=0
stuckKeys: value=0, 0=0, 1=0, 2=0, 3=0
stuckData: value=0, 0=0, 1=0, 2=0, 3=0
Stuck:  1   size: 0   free: 0   next:  0  leaf: 0
stuckSize: value=0
stuckKeys: value=0, 0=0, 1=0, 2=0, 3=0
stuckData: value=0, 0=0, 1=0, 2=0, 3=0
Stuck:  2   size: 0   free: 0   next:  0  leaf: 0
stuckSize: value=0
stuckKeys: value=0, 0=0, 1=0, 2=0, 3=0
stuckData: value=0, 0=0, 1=0, 2=0, 3=0
""");

    b.clearProgram();
    b.iFree(x);
    b.iFree(y);
    b.runProgram();
    ok(b.freeStart, "freeStart: value=2");
    ok(b.freeNext,  "freeNext: value=1, 0=0, 1=3, 2=1, 3=4, 4=5, 5=6, 6=7, 7=8, 8=9, 9=10, 10=11, 11=12, 12=13, 13=14, 14=15, 15=16, 16=17, 17=18, 18=19, 19=20, 20=21, 21=22, 22=23, 23=24, 24=25, 25=26, 26=27, 27=28, 28=29, 29=30, 30=31, 31=0");
    ok(b.dump(), """
Btree
Stuck:  0   size: 0   free: 0   next:  0  leaf: 1
stuckSize: value=0
stuckKeys: value=0, 0=0, 1=0, 2=0, 3=0
stuckData: value=0, 0=0, 1=0, 2=0, 3=0
""");
   }

  static Btree test_btree()
   {final Btree b = test_create();
    final Layout.Field s = b.index();
    final Layout.Field t = b.index();
    final Layout.Field x = b.index();
    final Layout.Field y = b.index();

    final Stuck S = b.stuck();
    final Stuck T = b.stuck();
    final Stuck X = b.stuck();
    final Stuck Y = b.stuck();
    final Stuck Z = b.stuck();
    b.iAllocate(s, true);
    b.iAllocate(t, true);
    b.iAllocate(x, true);
    b.iAllocate(y, true);
    b.runProgram();

    b.clearProgram();
    S.stuckKeys.iWrite( 1); S.stuckData.iWrite( 2); S.iPush();
    S.stuckKeys.iWrite( 2); S.stuckData.iWrite( 4); S.iPush();
    S.stuckKeys.iWrite( 3); S.stuckData.iWrite( 6); S.iPush();
    S.stuckKeys.iWrite( 4); S.stuckData.iWrite( 8); S.iPush();
    b.runProgram();

    b.clearProgram();
    T.stuckKeys.iWrite(11); T.stuckData.iWrite(12); T.iPush();
    T.stuckKeys.iWrite(12); T.stuckData.iWrite(14); T.iPush();
    T.stuckKeys.iWrite(13); T.stuckData.iWrite(16); T.iPush();
    T.stuckKeys.iWrite(14); T.stuckData.iWrite(18); T.iPush();
    b.runProgram();

    b.clearProgram();
    X.stuckKeys.iWrite(21); X.stuckData.iWrite(22); X.iPush();
    X.stuckKeys.iWrite(22); X.stuckData.iWrite(24); X.iPush();
    X.stuckKeys.iWrite(23); X.stuckData.iWrite(26); X.iPush();
    X.stuckKeys.iWrite(24); X.stuckData.iWrite(28); X.iPush();
    b.runProgram();

    b.clearProgram();
    Y.stuckKeys.iWrite(31); Y.stuckData.iWrite(32); Y.iPush();
    Y.stuckKeys.iWrite(32); Y.stuckData.iWrite(34); Y.iPush();
    Y.stuckKeys.iWrite(33); Y.stuckData.iWrite(36); Y.iPush();
    Y.stuckKeys.iWrite(34); Y.stuckData.iWrite(38); Y.iPush();
    b.runProgram();

    b.clearProgram();
    Z.stuckKeys.iWrite(10); Z.stuckData.iWrite(s.value); Z.iPush();
    Z.stuckKeys.iWrite(20); Z.stuckData.iWrite(t.value); Z.iPush();
    Z.stuckKeys.iWrite(30); Z.stuckData.iWrite(x.value); Z.iPush();
    Z.stuckKeys.iWrite(0);  Z.stuckData.iWrite(y.value); Z.iSetPastLastElement();
    b.runProgram();

    b.clearProgram();
    b.iSaveStuckInto(S, s);  b.iSetLeaf(s);
    b.iSaveStuckInto(T, t);  b.iSetLeaf(t);
    b.iSaveStuckInto(X, x);  b.iSetLeaf(x);
    b.iSaveStuckInto(Y, y);  b.iSetLeaf(y);
    b.iSaveStuckIntoRoot(Z); b.iSetRootAsBranch();
    b.runProgram();
  //stop(b.dump());
    ok(b.dump(), """
Btree
Stuck:  0   size: 3   free: 0   next:  0  leaf: 0
stuckSize: value=3
stuckKeys: value=30, 0=10, 1=20, 2=30, 3=0
stuckData: value=4, 0=1, 1=2, 2=3, 3=4
Stuck:  1   size: 4   free: 0   next:  0  leaf: 1
stuckSize: value=4
stuckKeys: value=4, 0=1, 1=2, 2=3, 3=4
stuckData: value=8, 0=2, 1=4, 2=6, 3=8
Stuck:  2   size: 4   free: 0   next:  0  leaf: 1
stuckSize: value=4
stuckKeys: value=14, 0=11, 1=12, 2=13, 3=14
stuckData: value=18, 0=12, 1=14, 2=16, 3=18
Stuck:  3   size: 4   free: 0   next:  0  leaf: 1
stuckSize: value=4
stuckKeys: value=24, 0=21, 1=22, 2=23, 3=24
stuckData: value=28, 0=22, 1=24, 2=26, 3=28
Stuck:  4   size: 4   free: 0   next:  0  leaf: 1
stuckSize: value=4
stuckKeys: value=34, 0=31, 1=32, 2=33, 3=34
stuckData: value=38, 0=32, 1=34, 2=36, 3=38
""");
    return b;
   }

  static void test_find()
   {final Btree b = test_btree();
    final Stuck s = b.stuck();
    final Layout.Field Key   = s.key();
    final Layout.Field Data  = s.data();
    final Layout.Field Found = s.found();
    final Layout.Field stuckIndex = s.index();
    final Layout.Field index = b.index();

    b.L.P.maxSteps = 500;

    b.clearProgram();
    Key.iWrite(5);
    b.find(Key, Found, Data, index, stuckIndex);
    b.runProgram();

    //stop(Found);
    ok(Found, "found: value=0");

    b.clearProgram();
    Key.iWrite(4);
    b.find(Key, Found, Data, index, stuckIndex);
    b.runProgram();

    //stop(Found, Data, index, stuckIndex);
    ok(Found, "found: value=1");
    ok(Data,  "data: value=8");
    ok(index, "index: value=1");
    ok(stuckIndex, "stuckIndex: value=3");

    b.clearProgram();
    Key.iWrite(14);
    b.find(Key, Found, Data, index, stuckIndex);
    b.runProgram();

    //stop(Found, Data, index, stuckIndex);
    ok(Found, "found: value=1");
    ok(Data,  "data: value=18");
    ok(index, "index: value=2");
    ok(stuckIndex, "stuckIndex: value=3");

    b.clearProgram();
    Key.iWrite(23);
    b.find(Key, Found, Data, index, stuckIndex);
    b.runProgram();

    //stop(Found, Data, index, stuckIndex);
    ok(Found, "found: value=1");
    ok(Data,  "data: value=26");
    ok(index, "index: value=3");
    ok(stuckIndex, "stuckIndex: value=2");

    b.clearProgram();
    Key.iWrite(32);
    b.find(Key, Found, Data, index, stuckIndex);
    b.runProgram();

    //stop(Found, Data, index, stuckIndex);
    ok(Found, "found: value=1");
    ok(Data,  "data: value=34");
    ok(index, "index: value=4");
    ok(stuckIndex, "stuckIndex: value=1");
   }

  static Btree test_findAndInsert()
   {final Btree b = test_create();
    final Layout.Field Found = b.found();

    b.L.P.maxSteps = 500;

    b.clearProgram();
    b.stuckKeys.iWrite(20);
    b.stuckData.iWrite(21);
    b.findAndInsert(Found);
    b.runProgram();
    ok(b.dump(), """
Btree
Stuck:  0   size: 1   free: 0   next:  0  leaf: 1
stuckSize: value=1
stuckKeys: value=20, 0=20, 1=0, 2=0, 3=0
stuckData: value=21, 0=21, 1=0, 2=0, 3=0
""");

    b.clearProgram();
    b.stuckKeys.iWrite(10);
    b.stuckData.iWrite(1);
    b.findAndInsert(Found);
    b.runProgram();
    ok(b.dump(), """
Btree
Stuck:  0   size: 2   free: 0   next:  0  leaf: 1
stuckSize: value=2
stuckKeys: value=20, 0=10, 1=20, 2=0, 3=0
stuckData: value=21, 0=1, 1=21, 2=0, 3=0
""");

    b.clearProgram();
    b.stuckKeys.iWrite(30);
    b.stuckData.iWrite(31);
    b.findAndInsert(Found);
    b.runProgram();
    ok(b.dump(), """
Btree
Stuck:  0   size: 3   free: 0   next:  0  leaf: 1
stuckSize: value=3
stuckKeys: value=30, 0=10, 1=20, 2=30, 3=0
stuckData: value=31, 0=1, 1=21, 2=31, 3=0
""");

    Layout.Field index = b.index();
    b.clearProgram();
    b.iAllocateLeaf(index);
    b.iSetRootAsBranch();
    b.runProgram();
    ok(b.dump(), """
Btree
Stuck:  0   size: 3   free: 0   next:  0  leaf: 0
stuckSize: value=3
stuckKeys: value=30, 0=10, 1=20, 2=30, 3=0
stuckData: value=0, 0=1, 1=21, 2=31, 3=0
Stuck:  1   size: 0   free: 0   next:  0  leaf: 1
stuckSize: value=0
stuckKeys: value=0, 0=0, 1=0, 2=0, 3=0
stuckData: value=0, 0=0, 1=0, 2=0, 3=0
""");

    b.clearProgram();
    b.stuckKeys.iWrite(4);
    b.stuckData.iWrite(5);
    b.findAndInsert(Found);
    b.runProgram();
    ok(b.dump(), """
Btree
Stuck:  0   size: 3   free: 0   next:  0  leaf: 0
stuckSize: value=3
stuckKeys: value=30, 0=10, 1=20, 2=30, 3=0
stuckData: value=0, 0=1, 1=21, 2=31, 3=0
Stuck:  1   size: 1   free: 0   next:  0  leaf: 1
stuckSize: value=1
stuckKeys: value=4, 0=4, 1=0, 2=0, 3=0
stuckData: value=5, 0=5, 1=0, 2=0, 3=0
""");

    b.clearProgram();
    b.stuckKeys.iWrite(5);
    b.stuckData.iWrite(6);
    b.findAndInsert(Found);
    b.runProgram();
    ok(b.dump(), """
Btree
Stuck:  0   size: 3   free: 0   next:  0  leaf: 0
stuckSize: value=3
stuckKeys: value=30, 0=10, 1=20, 2=30, 3=0
stuckData: value=0, 0=1, 1=21, 2=31, 3=0
Stuck:  1   size: 2   free: 0   next:  0  leaf: 1
stuckSize: value=2
stuckKeys: value=5, 0=4, 1=5, 2=0, 3=0
stuckData: value=6, 0=5, 1=6, 2=0, 3=0
""");

    b.clearProgram();
    b.stuckKeys.iWrite(3);
    b.stuckData.iWrite(4);
    b.findAndInsert(Found);
    b.runProgram();
    ok(b.dump(), """
Btree
Stuck:  0   size: 3   free: 0   next:  0  leaf: 0
stuckSize: value=3
stuckKeys: value=30, 0=10, 1=20, 2=30, 3=0
stuckData: value=0, 0=1, 1=21, 2=31, 3=0
Stuck:  1   size: 3   free: 0   next:  0  leaf: 1
stuckSize: value=3
stuckKeys: value=5, 0=3, 1=4, 2=5, 3=0
stuckData: value=6, 0=4, 1=5, 2=6, 3=0
""");
    return b;
   }

  static void test_isLeaf()
   {final Btree b = test_btree();
    final Layout.Field isLeaf     = b.isLeaf();
    final Layout.Field index = b.index();

    b.clearProgram();
    b.isRootLeaf(isLeaf);
    b.runProgram();
    ok(isLeaf, "isLeaf: value=0");

    b.clearProgram();
    index.iZero();
    b.iIsLeaf(index, isLeaf);
    b.runProgram();
    ok(isLeaf, "isLeaf: value=0");

    b.clearProgram();
    index.iOne();
    b.iIsLeaf(index, isLeaf);
    b.runProgram();
    ok(isLeaf, "isLeaf: value=1");
   }

  static void test_splitLeafRoot()
   {final Btree b = test_create();
    final Layout.Field Found = b.found();

    b.L.P.maxSteps = 500;

    b.clearProgram(); b.stuckKeys.iWrite(10); b.stuckData.iWrite(11); b.findAndInsert(Found); b.runProgram();
    b.clearProgram(); b.stuckKeys.iWrite(20); b.stuckData.iWrite(21); b.findAndInsert(Found); b.runProgram();
    b.clearProgram(); b.stuckKeys.iWrite(30); b.stuckData.iWrite(31); b.findAndInsert(Found); b.runProgram();
    b.clearProgram(); b.stuckKeys.iWrite(40); b.stuckData.iWrite(41); b.findAndInsert(Found); b.runProgram();
    ok(b.dump(), """
Btree
Stuck:  0   size: 4   free: 0   next:  0  leaf: 1
stuckSize: value=4
stuckKeys: value=40, 0=10, 1=20, 2=30, 3=40
stuckData: value=41, 0=11, 1=21, 2=31, 3=41
""");

    b.clearProgram();
    b.iSplitRootLeaf();
    b.runProgram();
    ok(b.dump(), """
Btree
Stuck:  0   size: 1   free: 0   next:  0  leaf: 0
stuckSize: value=1
stuckKeys: value=25, 0=25, 1=25, 2=30, 3=40
stuckData: value=2, 0=1, 1=2, 2=31, 3=41
Stuck:  1   size: 2   free: 0   next:  0  leaf: 1
stuckSize: value=2
stuckKeys: value=20, 0=10, 1=20, 2=0, 3=0
stuckData: value=21, 0=11, 1=21, 2=0, 3=0
Stuck:  2   size: 2   free: 0   next:  0  leaf: 1
stuckSize: value=2
stuckKeys: value=40, 0=30, 1=40, 2=0, 3=0
stuckData: value=41, 0=31, 1=41, 2=0, 3=0
""");
   }

  static void test_splitBranchRoot()
   {final Btree b = test_findAndInsert();

    b.L.P.maxSteps = 500;
//stop(b.dump());
    ok(b.dump(), """
Btree
Stuck:  0   size: 3   free: 0   next:  0  leaf: 0
stuckSize: value=3
stuckKeys: value=30, 0=10, 1=20, 2=30, 3=0
stuckData: value=0, 0=1, 1=21, 2=31, 3=0
Stuck:  1   size: 3   free: 0   next:  0  leaf: 1
stuckSize: value=3
stuckKeys: value=5, 0=3, 1=4, 2=5, 3=0
stuckData: value=6, 0=4, 1=5, 2=6, 3=0
""");

    b.clearProgram();
    b.iSplitRootBranch();
    b.runProgram();
    //stop(b.dump());
    ok(b.dump(), """
Btree
Stuck:  0   size: 1   free: 0   next:  0  leaf: 0
stuckSize: value=1
stuckKeys: value=20, 0=20, 1=20, 2=30, 3=0
stuckData: value=3, 0=2, 1=3, 2=31, 3=0
Stuck:  1   size: 3   free: 0   next:  0  leaf: 1
stuckSize: value=3
stuckKeys: value=5, 0=3, 1=4, 2=5, 3=0
stuckData: value=6, 0=4, 1=5, 2=6, 3=0
Stuck:  2   size: 1   free: 0   next:  0  leaf: 0
stuckSize: value=1
stuckKeys: value=10, 0=10, 1=0, 2=0, 3=0
stuckData: value=21, 0=1, 1=21, 2=0, 3=0
Stuck:  3   size: 1   free: 0   next:  0  leaf: 0
stuckSize: value=1
stuckKeys: value=30, 0=30, 1=0, 2=0, 3=0
stuckData: value=0, 0=31, 1=0, 2=0, 3=0
""");
   }

  static void test_splitLeafNotTop()
   {final Btree b = test_create();
    final Stuck r = b.stuck();
    final Stuck l = b.stuck();
    final Layout.Field Found = b.found();
    final Layout.Field R     = b.index();
    final Layout.Field I     = r.index();
    final Layout.Field L     = b.index();

    b.L.P.maxSteps = 500;

    b.clearProgram(); r.stuckKeys.iWrite(10); r.stuckData.iWrite(1); r.iPush();               b.runProgram();
    b.clearProgram(); r.stuckKeys.iWrite(20); r.stuckData.iWrite(0); r.iPush();               b.runProgram();
    b.clearProgram(); r.stuckKeys.iWrite(30); r.stuckData.iWrite(0); r.iSetPastLastElement(); b.runProgram();
    b.clearProgram(); b.iSaveStuckIntoRoot(r);                                                 b.runProgram();

    b.clearProgram(); l.stuckKeys.iWrite(1); l.stuckData.iWrite(1); l.iPush(); b.runProgram();
    b.clearProgram(); l.stuckKeys.iWrite(2); l.stuckData.iWrite(2); l.iPush(); b.runProgram();
    b.clearProgram(); l.stuckKeys.iWrite(3); l.stuckData.iWrite(3); l.iPush(); b.runProgram();
    b.clearProgram(); l.stuckKeys.iWrite(4); l.stuckData.iWrite(4); l.iPush(); b.runProgram();

    b.clearProgram();
    b.iSaveStuckIntoRoot(r);                     b.iSetRootAsBranch();
    b.iAllocateLeaf(L); b.iSaveStuckInto(l, L);  b.iSetLeaf(L);
    b.runProgram();
    ok(b.dump(), """
Btree
Stuck:  0   size: 2   free: 0   next:  0  leaf: 0
stuckSize: value=2
stuckKeys: value=20, 0=10, 1=20, 2=30, 3=0
stuckData: value=0, 0=1, 1=0, 2=0, 3=0
Stuck:  1   size: 4   free: 0   next:  0  leaf: 1
stuckSize: value=4
stuckKeys: value=4, 0=1, 1=2, 2=3, 3=4
stuckData: value=4, 0=1, 1=2, 2=3, 3=4
""");

    b.clearProgram();
    R.iZero();
    I.iZero();
    b.iSplitLeafNotTop(R, I);
    b.runProgram();
    ok(b.dump(), """
Btree
Stuck:  0   size: 3   free: 0   next:  0  leaf: 0
stuckSize: value=3
stuckKeys: value=20, 0=2, 1=10, 2=20, 3=30
stuckData: value=0, 0=2, 1=1, 2=0, 3=0
Stuck:  1   size: 2   free: 0   next:  0  leaf: 1
stuckSize: value=2
stuckKeys: value=4, 0=3, 1=4, 2=3, 3=4
stuckData: value=4, 0=3, 1=4, 2=3, 3=4
Stuck:  2   size: 2   free: 0   next:  0  leaf: 1
stuckSize: value=2
stuckKeys: value=2, 0=1, 1=2, 2=0, 3=0
stuckData: value=2, 0=1, 1=2, 2=0, 3=0
""");
   }

  static void test_splitLeafAtTop()
   {final Btree b = test_create();
    final Stuck r = b.stuck();
    final Stuck l = b.stuck();
    final Layout.Field Found = b.found();
    final Layout.Field R     = b.index();
    final Layout.Field L     = b.index();

    b.L.P.maxSteps = 500;

    b.clearProgram(); r.stuckKeys.iWrite(10); r.stuckData.iWrite(0); r.iPush();               b.runProgram();
    b.clearProgram(); r.stuckKeys.iWrite(20); r.stuckData.iWrite(0); r.iPush();               b.runProgram();
    b.clearProgram(); r.stuckKeys.iWrite(30); r.stuckData.iWrite(1); r.iSetPastLastElement(); b.runProgram();
    b.clearProgram(); b.iSaveStuckIntoRoot(r);                                                 b.runProgram();

    b.clearProgram(); l.stuckKeys.iWrite(1); l.stuckData.iWrite(1); l.iPush(); b.runProgram();
    b.clearProgram(); l.stuckKeys.iWrite(2); l.stuckData.iWrite(2); l.iPush(); b.runProgram();
    b.clearProgram(); l.stuckKeys.iWrite(3); l.stuckData.iWrite(3); l.iPush(); b.runProgram();
    b.clearProgram(); l.stuckKeys.iWrite(4); l.stuckData.iWrite(4); l.iPush(); b.runProgram();

    b.clearProgram();
    b.iSaveStuckIntoRoot(r);                       b.iSetRootAsBranch();
    b.iAllocateBranch(L); b.iSaveStuckInto(l, L);  b.iSetLeaf(L);
    b.runProgram();
    //stop(b.dump());
    ok(b.dump(), """
Btree
Stuck:  0   size: 2   free: 0   next:  0  leaf: 0
stuckSize: value=2
stuckKeys: value=20, 0=10, 1=20, 2=30, 3=0
stuckData: value=1, 0=0, 1=0, 2=1, 3=0
Stuck:  1   size: 4   free: 0   next:  0  leaf: 1
stuckSize: value=4
stuckKeys: value=4, 0=1, 1=2, 2=3, 3=4
stuckData: value=4, 0=1, 1=2, 2=3, 3=4
""");

    b.clearProgram();
    R.iZero();
    b.iSplitLeafAtTop(R);
    b.runProgram();
    //stop(b.dump());
    ok(b.dump(), """
Btree
Stuck:  0   size: 3   free: 0   next:  0  leaf: 0
stuckSize: value=3
stuckKeys: value=2, 0=10, 1=20, 2=2, 3=0
stuckData: value=1, 0=0, 1=0, 2=2, 3=1
Stuck:  1   size: 2   free: 0   next:  0  leaf: 1
stuckSize: value=2
stuckKeys: value=4, 0=3, 1=4, 2=3, 3=4
stuckData: value=4, 0=3, 1=4, 2=3, 3=4
Stuck:  2   size: 2   free: 0   next:  0  leaf: 1
stuckSize: value=2
stuckKeys: value=2, 0=1, 1=2, 2=0, 3=0
stuckData: value=2, 0=1, 1=2, 2=0, 3=0
""");
   }

  static void test_splitBranchNotTop()
   {final Btree b = test_create();
    final Stuck r = b.stuck();
    final Stuck l = b.stuck();
    final Layout.Field Found = b.found();
    final Layout.Field R     = b.index();
    final Layout.Field I     = r.index();
    final Layout.Field L     = b.index();

    b.L.P.maxSteps = 500;

    b.clearProgram(); r.stuckKeys.iWrite(10); r.stuckData.iWrite(1); r.iPush();               b.runProgram();
    b.clearProgram(); r.stuckKeys.iWrite(20); r.stuckData.iWrite(0); r.iPush();               b.runProgram();
    b.clearProgram(); r.stuckKeys.iWrite(30); r.stuckData.iWrite(0); r.iSetPastLastElement(); b.runProgram();
    b.clearProgram(); b.iSaveStuckIntoRoot(r);                                                b.runProgram();

    b.clearProgram(); l.stuckKeys.iWrite(1); l.stuckData.iWrite(2); l.iPush(); b.runProgram();
    b.clearProgram(); l.stuckKeys.iWrite(2); l.stuckData.iWrite(3); l.iPush(); b.runProgram();
    b.clearProgram(); l.stuckKeys.iWrite(3); l.stuckData.iWrite(4); l.iPush(); b.runProgram();
    b.clearProgram(); l.stuckKeys.iWrite(4); l.stuckData.iWrite(5); l.iSetPastLastElement(); b.runProgram();

    b.clearProgram();
    b.iSaveStuckIntoRoot(r);                      b.iSetRootAsBranch();
    b.iAllocateBranch(L); b.iSaveStuckInto(l, L); b.iSetBranch(L);
    b.runProgram();
    //stop(b.dump());
    ok(b.dump(), """
Btree
Stuck:  0   size: 2   free: 0   next:  0  leaf: 0
stuckSize: value=2
stuckKeys: value=20, 0=10, 1=20, 2=30, 3=0
stuckData: value=0, 0=1, 1=0, 2=0, 3=0
Stuck:  1   size: 3   free: 0   next:  0  leaf: 0
stuckSize: value=3
stuckKeys: value=3, 0=1, 1=2, 2=3, 3=4
stuckData: value=5, 0=2, 1=3, 2=4, 3=5
""");

    b.clearProgram();
    L.iZero();
    I.iZero();
    b.iSplitBranchNotTop(L, I);
    b.runProgram();
    //stop(b.dump());
    ok(b.dump(), """
Btree
Stuck:  0   size: 3   free: 0   next:  0  leaf: 0
stuckSize: value=3
stuckKeys: value=20, 0=2, 1=10, 2=20, 3=30
stuckData: value=0, 0=2, 1=1, 2=0, 3=0
Stuck:  1   size: 1   free: 0   next:  0  leaf: 0
stuckSize: value=1
stuckKeys: value=3, 0=3, 1=2, 2=3, 3=4
stuckData: value=5, 0=4, 1=5, 2=4, 3=5
Stuck:  2   size: 1   free: 0   next:  0  leaf: 0
stuckSize: value=1
stuckKeys: value=1, 0=1, 1=0, 2=0, 3=0
stuckData: value=3, 0=2, 1=3, 2=0, 3=0
""");
   }

  static void test_splitBranchAtTop()
   {final Btree b = test_create();
    final Stuck r = b.stuck();
    final Stuck l = b.stuck();
    final Layout.Field Found = b.found();
    final Layout.Field R     = b.index();
    final Layout.Field L     = b.index();

    b.L.P.maxSteps = 500;

    b.clearProgram(); r.stuckKeys.iWrite(10); r.stuckData.iWrite(0); r.iPush();               b.runProgram();
    b.clearProgram(); r.stuckKeys.iWrite(20); r.stuckData.iWrite(0); r.iPush();               b.runProgram();
    b.clearProgram(); r.stuckKeys.iWrite(30); r.stuckData.iWrite(1); r.iSetPastLastElement(); b.runProgram();
    b.clearProgram(); b.iSaveStuckIntoRoot(r);                                                 b.runProgram();

    b.clearProgram(); l.stuckKeys.iWrite(1); l.stuckData.iWrite(1); l.iPush();                b.runProgram();
    b.clearProgram(); l.stuckKeys.iWrite(2); l.stuckData.iWrite(2); l.iPush();                b.runProgram();
    b.clearProgram(); l.stuckKeys.iWrite(3); l.stuckData.iWrite(3); l.iPush();                b.runProgram();
    b.clearProgram(); l.stuckKeys.iWrite(4); l.stuckData.iWrite(4); l.iSetPastLastElement();  b.runProgram();

    b.clearProgram();
    b.iSaveStuckIntoRoot(r);                       b.iSetRootAsBranch();
    b.iAllocateBranch(L); b.iSaveStuckInto(l, L);  b.iSetBranch(L);
    b.runProgram();
    //stop(b.dump());
    ok(b.dump(), """
Btree
Stuck:  0   size: 2   free: 0   next:  0  leaf: 0
stuckSize: value=2
stuckKeys: value=20, 0=10, 1=20, 2=30, 3=0
stuckData: value=1, 0=0, 1=0, 2=1, 3=0
Stuck:  1   size: 3   free: 0   next:  0  leaf: 0
stuckSize: value=3
stuckKeys: value=3, 0=1, 1=2, 2=3, 3=4
stuckData: value=4, 0=1, 1=2, 2=3, 3=4
""");

    b.clearProgram();
    R.iZero();
    b.iSplitBranchAtTop(R);
    b.runProgram();
    //stop(b.dump());
    ok(b.dump(), """
Btree
Stuck:  0   size: 3   free: 0   next:  0  leaf: 0
stuckSize: value=3
stuckKeys: value=2, 0=10, 1=20, 2=2, 3=0
stuckData: value=1, 0=0, 1=0, 2=2, 3=1
Stuck:  1   size: 1   free: 0   next:  0  leaf: 0
stuckSize: value=1
stuckKeys: value=3, 0=3, 1=2, 2=3, 3=4
stuckData: value=4, 0=3, 1=4, 2=3, 3=4
Stuck:  2   size: 1   free: 0   next:  0  leaf: 0
stuckSize: value=1
stuckKeys: value=1, 0=1, 1=0, 2=0, 3=0
stuckData: value=2, 0=1, 1=2, 2=0, 3=0
""");
   }

  static void test_isRootLeafFull()
   {final Btree b = test_create();
    final Layout.Field f = b.leafFull();
    final Layout.Field F = b.branchFull();

    b.clearProgram(); b.stuckKeys.iWrite(10); b.stuckData.iWrite(11); b.findAndInsert(f); b.isRootLeafFull(f); b.iIsRootBranchFull(F); b.runProgram(); ok(f, "leafFull: value=0"); ok(F, "branchFull: value=0");
    b.clearProgram(); b.stuckKeys.iWrite(20); b.stuckData.iWrite(21); b.findAndInsert(f); b.isRootLeafFull(f); b.iIsRootBranchFull(F); b.runProgram(); ok(f, "leafFull: value=0"); ok(F, "branchFull: value=0");
    b.clearProgram(); b.stuckKeys.iWrite(30); b.stuckData.iWrite(31); b.findAndInsert(f); b.isRootLeafFull(f); b.iIsRootBranchFull(F); b.runProgram(); ok(f, "leafFull: value=0"); ok(F, "branchFull: value=1");
    b.clearProgram(); b.stuckKeys.iWrite(40); b.stuckData.iWrite(41); b.findAndInsert(f); b.isRootLeafFull(f); b.iIsRootBranchFull(F); b.runProgram(); ok(f, "leafFull: value=1"); ok(F, "branchFull: value=1");
   }

  static void test_put()
   {final Btree b = test_create();

    b.L.P.maxSteps = 2000;

    final int N = 32;
    for (int i = 1; i <= N; i++)
     {b.clearProgram();
      b.stuckKeys.iWrite(i);
      b.stuckData.iWrite(i+1);
      b.put();
      b.runProgram();
     }
    //stop(b);
    ok(b, """
                                                      16                                                                   |
                                                      0                                                                    |
                                                      6                                                                    |
                                                      11                                                                   |
          4          8               12                               20               24                28                |
          6          6.1             6.2                              11               11.1              11.2              |
          1          3               4                                8                10                9                 |
                                     7                                                                   2                 |
1,2,3,4=1  5,6,7,8=3    9,10,11,12=4    13,14,15,16=7   17,18,19,20=8   21,22,23,24=10     25,26,27,28=9     29,30,31,32=2 |
""");
   }

  static void test_putReverse()
   {final Btree b = test_create();

    b.L.P.maxSteps = 2000;

    final int N = 32;
    for (int i = N; i > 0; i--)
     {b.clearProgram();
      b.stuckKeys.iWrite(i);
      b.stuckData.iWrite(i+1);
      b.put();
      b.runProgram();
     }
    //stop(b);
    ok(b, """
                                                           16                                                                |
                                                           0                                                                 |
                                                           11                                                                |
                                                           6                                                                 |
           4            8                12                                20              24               28               |
           11           11.1             11.2                              6               6.1              6.2              |
           12           10               8                                 4               3                2                |
                                         7                                                                  1                |
1,2,3,4=12   5,6,7,8=10     9,10,11,12=8     13,14,15,16=7   17,18,19,20=4   21,22,23,24=3    25,26,27,28=2    29,30,31,32=1 |
""");
   }

  static void test_putRandom()
   {final Btree b = new Btree(64, 4, 16, 16);

    b.L.P.maxSteps = 8000;

    final int N = 32;
    for (int i = 0; i < random_100.length; ++i)
     {b.clearProgram();
      b.stuckKeys.iWrite(random_100[i]);
      b.stuckData.iWrite(i);
      b.put();
      b.runProgram();
     }
    //stop(b);
    ok(b, """
                                                                                                                                                                                                                        379                                                                                                                    528                                                                                                                                                                                                                                                                  |
                                                                                                                                                                                                                        0                                                                                                                      0.1                                                                                                                                                                                                                                                                  |
                                                                                                                                                                                                                        48                                                                                                                     34                                                                                                                                                                                                                                                                   |
                                                                                                                                                                                                                                                                                                                                               39                                                                                                                                                                                                                                                                   |
                                                               143                                                              253                                                     341                                                             429                                                   497                                                                     582                                                                          718                                                                      894                                                                     |
                                                               48                                                               48.1                                                    48.2                                                            34                                                    34.1                                                                    39                                                                           39.1                                                                     39.2                                                                    |
                                                               36                                                               25                                                      46                                                              14                                                    41                                                                      21                                                                           11                                                                       3                                                                       |
                                                                                                                                                                                        10                                                                                                                    5                                                                                                                                                                                                                             6                                                                       |
              34               87               104                          156               210                235                          266               283                                   356                            402                                 440          457                                   507                                     568                                    630                   672                688                              805           819                   856                                  909                   946               988          |
              36               36.1             36.2                         25                25.1               25.2                         46                46.1                                  10                             14                                  41           41.1                                  5                                       21                                     11                    11.1               11.2                             3             3.1                   3.2                                  6                     6.1               6.2          |
              37               24               45                           49                26                 38                           47                30                                    17                             15                                  44           8                                     33                                      18                                     27                    13                 42                               29            43                    32                                   40                    12                35           |
                                                16                                                                9                                              19                                    1                              20                                               28                                    22                                      7                                                                               4                                                                    23                                                                           2            |
1,13,27,29=37   39,43,55,72=24     90,96,103=45     106,135=16    151,155=49    157,186,188=26     229,232,234=38     237,246=9     260,261=47    272,273,279=30     288,298,317,338=19     344,354=17    358,376,377=1    391,401=15    403,422,425=20    436,437,438=44    442,447=8     472,480,490,494=28     501,503=33    511,516,526=22    545,554,560,564=18    576,577,578=7    586,611,612,615=27    650,657,658,667=13     679,681,686=42     690,704=4     769,773,804=29    806,809=43    826,830,839,854=32    858,882,884=23     903,906,907=40    912,922,937,946=12    961,976,987=35    989,993=2 |
""");
   }

  static void test_mergeLeavesIntoRoot()
   {final Btree b = test_create();
    final Stuck s = b.stuck();
    final Layout.Field index = b.index();
    final Layout.Field success = s.success();
    b.L.P.maxSteps = 2000;

    final int N = 6;
    for (int i = 1; i <= N; i++)
     {b.clearProgram();
      b.stuckKeys.iWrite(i);
      b.stuckData.iWrite(i+1);
      b.put();
      b.runProgram();
     }
    index.value = 2;
    b.clearProgram();
    b.iCopyStuckFrom(s, index);
    s.iPop(); s.iPop();
    b.iSaveStuckInto(s, index);
    b.runProgram();
    //stop(b);
    ok(b, """
      2      |
      0      |
      1      |
      2      |
1,2=1  3,4=2 |
""");
    b.clearProgram();
    b.iMergeLeavesIntoRoot(success);
    b.runProgram();
    //stop(b);
    ok(b, """
1,2,3,4=0 |
""");
   }

  static void test_mergeLeavesNotTop()
   {final Btree b = test_create();
    final Stuck s = b.stuck();
    final Layout.Field index = b.index();
    final Layout.Field stuckIndex = s.index();
    final Layout.Field success = s.success();
    b.L.P.maxSteps = 2000;
    b.supressMerge = true;

    final int N = 10;
    for (int i = 1; i <= N; i++)
     {b.clearProgram();
      b.stuckKeys.iWrite(i);
      b.stuckData.iWrite(i+1);
      b.put();
      b.runProgram();
     }
    //stop(b);
    ok(b, """
      2      4        6             |
      0      0.1      0.2           |
      1      3        4             |
                      2             |
1,2=1  3,4=3    5,6=4    7,8,9,10=2 |
""");
    index.value = 0;
    stuckIndex.value = 0;
    b.clearProgram();
    b.iMergeLeavesNotTop(index, stuckIndex, success);
    b.runProgram();
    ok(success, "success: value=1");
    //stop(b);
    ok(b, """
          4      6             |
          0      0.1           |
          1      4             |
                 2             |
1,2,3,4=1  5,6=4    7,8,9,10=2 |
""");
   }

  static void test_mergeLeavesAtTop()
   {final Btree b = test_create();
    final Stuck s = b.stuck();
    final Layout.Field index = b.index();
    final Layout.Field success = s.success();
    b.L.P.maxSteps = 2000;
    b.supressMerge = true;

    final int N = 6;
    for (int i = 1; i <= N; i++)
     {b.clearProgram();
      b.stuckKeys.iWrite(i);
      b.stuckData.iWrite(i+1);
      b.put();
      b.runProgram();
     }
    //stop(b);
    index.value = 2;
    b.clearProgram();
    b.iCopyStuckFrom(s, index);
    s.iPop(); s.iPop();
    b.iSaveStuckInto(s, index);
    b.runProgram();
    //stop(b);
    ok(b, """
      2      |
      0      |
      1      |
      2      |
1,2=1  3,4=2 |
""");
    index.value = 0;
    b.clearProgram();
    b.iMergeLeavesAtTop(index, success);
    b.runProgram();
    ok(success, "success: value=1");
    //stop(b);
    ok(b, """
0Empty          |
1               |
      1,2,3,4=1 |
""");
   }

  static void test_mergeBranchesIntoRoot()
   {final Btree b = test_create();
    final Stuck s = b.stuck();
    final Layout.Field index = b.index();
    final Layout.Field success = s.success();
    b.L.P.maxSteps = 2000;
    b.supressMerge = true;

    final int N = 11;
    for (int i = 1; i <= N; i++)
     {b.clearProgram();
      b.stuckKeys.iWrite(i);
      b.stuckData.iWrite(i+1);
      b.put();
      b.runProgram();
     }
    //stop(b);
    index.value = 6;
    b.clearProgram();
    b.iCopyStuckFrom(s, index);
    s.iPop();
    b.iSaveStuckInto(s, index);
    b.runProgram();
    //stop(b);
    ok(b, """
             4             |
             0             |
             5             |
             6             |
      2             6      |
      5             6      |
      1             4      |
      3             7      |
1,2=1  3,4=3  5,6=4  7,8=7 |
""");
    b.clearProgram();
    b.iMergeBranchesIntoRoot(success);
    b.runProgram();
    ok(success, "success: value=1");
    //stop(b);
    ok(b, """
      2      4        6        |
      0      0.1      0.2      |
      1      3        4        |
                      7        |
1,2=1  3,4=3    5,6=4    7,8=7 |
""");
   }

  static void test_mergeBranchesNotTop()
   {final Btree b = test_create();
    final Stuck s = b.stuck();
    final Layout.Field index = b.index();
    final Layout.Field stuckIndex = s.index();
    final Layout.Field success = s.success();
    b.L.P.maxSteps = 2000;
    b.supressMerge = true;

    final int N = 20;
    for (int i = 1; i <= N; i++)
     {b.clearProgram();
      b.stuckKeys.iWrite(i);
      b.stuckData.iWrite(i+1);
      b.put();
      b.runProgram();
     }
    //stop(b);
    ok(b, """
             4             8                    12                                      |
             0             0.1                  0.2                                     |
             5             9                    12                                      |
                                                6                                       |
      2             6                10                     14         16               |
      5             9                12                     6          6.1              |
      1             4                8                      11         13               |
      3             7                10                                2                |
1,2=1  3,4=3  5,6=4  7,8=7    9,10=8   11,12=10    13,14=11   15,16=13    17,18,19,20=2 |
""");
    index.value = 0;
    stuckIndex.value = 0;
    b.clearProgram();
    b.iMergeBranchesNotTop(index, stuckIndex, success);
    b.runProgram();
    ok(success, "success: value=1");
    //stop(b);
    ok(b, """
                               8                  12                                      |
                               0                  0.1                                     |
                               5                  12                                      |
                                                  6                                       |
      2      4        6                10                     14         16               |
      5      5.1      5.2              12                     6          6.1              |
      1      3        4                8                      11         13               |
                      7                10                                2                |
1,2=1  3,4=3    5,6=4    7,8=7  9,10=8   11,12=10    13,14=11   15,16=13    17,18,19,20=2 |
""");
   }

  static void test_mergeBranchesAtTop()
   {final Btree b = test_create();
    final Stuck s = b.stuck();
    final Layout.Field index = b.index();
    final Layout.Field success = s.success();
    b.L.P.maxSteps = 2000;
    b.supressMerge = true;

    final int N = 15;
    for (int i = 1; i <= N; i++)
     {b.clearProgram();
      b.stuckKeys.iWrite(i);
      b.stuckData.iWrite(i+1);
      b.put();
      b.runProgram();
     }
    //stop(b);
    index.value = 6;
    b.clearProgram();
    b.iCopyStuckFrom(s, index);
    s.iPop();
    b.iSaveStuckInto(s, index);
    b.runProgram();
    //stop(b);
    ok(b, """
             4             8                    |
             0             0.1                  |
             5             9                    |
                           6                    |
      2             6                10         |
      5             9                6          |
      1             4                8          |
      3             7                10         |
1,2=1  3,4=3  5,6=4  7,8=7    9,10=8   11,12=10 |
""");

    index.value = 0;
    b.clearProgram();
    b.iMergeBranchesAtTop(index, success);
    b.runProgram();
    ok(success, "success: value=1");
    //stop(b);
    ok(b, """
             4                                   |
             0                                   |
             5                                   |
             9                                   |
      2             6      8         10          |
      5             9      9.1       9.2         |
      1             4      7         8           |
      3                              10          |
1,2=1  3,4=3  5,6=4  7,8=7    9,10=8    11,12=10 |
""");
   }

  static void test_merge()
   {final Btree b = test_create();
    b.L.P.maxSteps = 9000;
    b.supressMerge = true;

    final int N = 32;
    for (int i = 1; i <= N; i++)
     {b.clearProgram();
      b.stuckKeys.iWrite(i);
      b.stuckData.iWrite(i+1);
      b.put();
      b.runProgram();
     }
    //stop(b);
    ok(b, """
                            8                                         16                                                                                    |
                            0                                         0.1                                                                                   |
                            14                                        22                                                                                    |
                                                                      15                                                                                    |
             4                                  12                                           20                    24                                       |
             14                                 22                                           15                    15.1                                     |
             5                                  12                                           20                    24                                       |
             9                                  17                                                                 6                                        |
      2              6               10                    14                     18                    22                      26         28               |
      5              9               12                    17                     20                    24                      6          6.1              |
      1              4               8                     11                     16                    19                      23         25               |
      3              7               10                    13                     18                    21                                 2                |
1,2=1  3,4=3   5,6=4  7,8=7   9,10=8   11,12=10   13,14=11   15,16=13    17,18=16   19,20=18   21,22=19   23,24=21     25,26=23   27,28=25    29,30,31,32=2 |
""");

    b.stuckKeys.value = 1; b.clearProgram(); b.merge(); b.runProgram();
    //stop(b);
    ok(b, """
                                                                   16                                                                                   |
                                                                   0                                                                                    |
                                                                   14                                                                                   |
                                                                   15                                                                                   |
                     8                                                                   20                    24                                       |
                     14                                                                  15                    15.1                                     |
                     5                                                                   20                    24                                       |
                     12                                                                                        6                                        |
          4                   10         12           14                      18                    22                      26         28               |
          5                   12         12.1         12.2                    20                    24                      6          6.1              |
          1                   8          10           11                      16                    19                      23         25               |
          4                                           13                      18                    21                                 2                |
1,2,3,4=1  5,6,7,8=4   9,10=8   11,12=10     13,14=11     15,16=13   17,18=16   19,20=18   21,22=19   23,24=21     25,26=23   27,28=25    29,30,31,32=2 |
""");

    b.stuckKeys.value = 10; b.clearProgram(); b.merge(); b.runProgram();
    //stop(b);
    ok(b, """
                                                     16                                                                                   |
                                                     0                                                                                    |
                                                     14                                                                                   |
                                                     15                                                                                   |
                     8                                                     20                    24                                       |
                     14                                                    15                    15.1                                     |
                     5                                                     20                    24                                       |
                     12                                                                          6                                        |
          4                         12                          18                    22                      26         28               |
          5                         12                          20                    24                      6          6.1              |
          1                         8                           16                    19                      23         25               |
          4                         11                          18                    21                                 2                |
1,2,3,4=1  5,6,7,8=4   9,10,11,12=8   13,14,15,16=11   17,18=16   19,20=18   21,22=19   23,24=21     25,26=23   27,28=25    29,30,31,32=2 |
""");

    b.stuckKeys.value = 18; b.clearProgram(); b.merge(); b.runProgram();
    //stop(b);
    ok(b, """
                                                     16                                                                       |
                                                     0                                                                        |
                                                     14                                                                       |
                                                     15                                                                       |
                     8                                                                 24                                     |
                     14                                                                15                                     |
                     5                                                                 20                                     |
                     12                                                                6                                      |
          4                         12                                20                          26         28               |
          5                         12                                20                          6          6.1              |
          1                         8                                 16                          23         25               |
          4                         11                                19                                     2                |
1,2,3,4=1  5,6,7,8=4   9,10,11,12=8   13,14,15,16=11   17,18,19,20=16   21,22,23,24=19   25,26=23   27,28=25    29,30,31,32=2 |
""");

    b.stuckKeys.value = 26; b.clearProgram(); b.merge(); b.runProgram();
    //stop(b);
    ok(b, """
                                                       16                                24                                |
                                                       0                                 0.1                               |
                                                       5                                 20                                |
                                                                                         6                                 |
          4          8               12                                 20                                 28              |
          5          5.1             5.2                                20                                 6               |
          1          4               8                                  16                                 23              |
                                     11                                 19                                 2               |
1,2,3,4=1  5,6,7,8=4    9,10,11,12=8    13,14,15,16=11   17,18,19,20=16   21,22,23,24=19    25,26,27,28=23   29,30,31,32=2 |
""");

    b.stuckKeys.value = 32; b.clearProgram(); b.merge(); b.runProgram();
    //stop(b);
    ok(b, """
                                                       16                                                                     |
                                                       0                                                                      |
                                                       5                                                                      |
                                                       20                                                                     |
          4          8               12                                 20               24                 28                |
          5          5.1             5.2                                20               20.1               20.2              |
          1          4               8                                  16               19                 23                |
                                     11                                                                     2                 |
1,2,3,4=1  5,6,7,8=4    9,10,11,12=8    13,14,15,16=11   17,18,19,20=16   21,22,23,24=19     25,26,27,28=23     29,30,31,32=2 |
""");
   }

  static void test_delete()
   {final Btree b = test_create();
    final Stuck s = b.stuck();
    final Layout.Field d = s.data();
    b.L.P.maxSteps = 9000;

    final int N = 32;
    for (int i = 1; i <= N; i++)
     {b.clearProgram();
      b.stuckKeys.iWrite(i);
      b.stuckData.iWrite(i+1);
      b.put();
      b.runProgram();
     }
    //stop(b);
    ok(b, """
                                                      16                                                                   |
                                                      0                                                                    |
                                                      6                                                                    |
                                                      11                                                                   |
          4          8               12                               20               24                28                |
          6          6.1             6.2                              11               11.1              11.2              |
          1          3               4                                8                10                9                 |
                                     7                                                                   2                 |
1,2,3,4=1  5,6,7,8=3    9,10,11,12=4    13,14,15,16=7   17,18,19,20=8   21,22,23,24=10     25,26,27,28=9     29,30,31,32=2 |
""");

    final StringBuilder t = new StringBuilder();
    for (int i = N; i >= 2; i -= 2)                                             // Delete evens in reverse
     {b.clearProgram();
      b.stuckKeys.iWrite(i);
      b.delete(d);
      b.runProgram();
      t.append(""+b);
      ok(d, "data: value="+(i+1));
     }
    for (int i = 1; i <= N; i += 2)                                             // Delete odds
     {b.clearProgram();
      b.stuckKeys.iWrite(i);
      b.delete(d);
      b.runProgram();
      t.append(""+b);
      ok(d, "data: value="+(i+1));
     }
    //stop(t);
    ok(""+t, """
                                                      16                                                                |
                                                      0                                                                 |
                                                      6                                                                 |
                                                      11                                                                |
          4          8               12                               20               24                28             |
          6          6.1             6.2                              11               11.1              11.2           |
          1          3               4                                8                10                9              |
                                     7                                                                   2              |
1,2,3,4=1  5,6,7,8=3    9,10,11,12=4    13,14,15,16=7   17,18,19,20=8   21,22,23,24=10     25,26,27,28=9     29,30,31=2 |
                                                      16                                                             |
                                                      0                                                              |
                                                      6                                                              |
                                                      11                                                             |
          4          8               12                               20               24                28          |
          6          6.1             6.2                              11               11.1              11.2        |
          1          3               4                                8                10                9           |
                                     7                                                                   2           |
1,2,3,4=1  5,6,7,8=3    9,10,11,12=4    13,14,15,16=7   17,18,19,20=8   21,22,23,24=10     25,26,27,28=9     29,31=2 |
                                                      16                                                          |
                                                      0                                                           |
                                                      6                                                           |
                                                      11                                                          |
          4          8               12                               20               24             28          |
          6          6.1             6.2                              11               11.1           11.2        |
          1          3               4                                8                10             9           |
                                     7                                                                2           |
1,2,3,4=1  5,6,7,8=3    9,10,11,12=4    13,14,15,16=7   17,18,19,20=8   21,22,23,24=10     25,26,27=9     29,31=2 |
                                                      16                                                 |
                                                      0                                                  |
                                                      6                                                  |
                                                      11                                                 |
          4          8               12                               20               24                |
          6          6.1             6.2                              11               11.1              |
          1          3               4                                8                10                |
                                     7                                                 9                 |
1,2,3,4=1  5,6,7,8=3    9,10,11,12=4    13,14,15,16=7   17,18,19,20=8   21,22,23,24=10     25,27,29,31=9 |
                                                      16                                              |
                                                      0                                               |
                                                      6                                               |
                                                      11                                              |
          4          8               12                               20            24                |
          6          6.1             6.2                              11            11.1              |
          1          3               4                                8             10                |
                                     7                                              9                 |
1,2,3,4=1  5,6,7,8=3    9,10,11,12=4    13,14,15,16=7   17,18,19,20=8   21,22,23=10     25,27,29,31=9 |
                                                      16                                           |
                                                      0                                            |
                                                      6                                            |
                                                      11                                           |
          4          8               12                               20         24                |
          6          6.1             6.2                              11         11.1              |
          1          3               4                                8          10                |
                                     7                                           9                 |
1,2,3,4=1  5,6,7,8=3    9,10,11,12=4    13,14,15,16=7   17,18,19,20=8   21,23=10     25,27,29,31=9 |
                                                      16                                        |
                                                      0                                         |
                                                      6                                         |
                                                      11                                        |
          4          8               12                            20         24                |
          6          6.1             6.2                           11         11.1              |
          1          3               4                             8          10                |
                                     7                                        9                 |
1,2,3,4=1  5,6,7,8=3    9,10,11,12=4    13,14,15,16=7   17,18,19=8   21,23=10     25,27,29,31=9 |
                                                      16                              |
                                                      0                               |
                                                      6                               |
                                                      11                              |
          4          8               12                               24              |
          6          6.1             6.2                              11              |
          1          3               4                                8               |
                                     7                                9               |
1,2,3,4=1  5,6,7,8=3    9,10,11,12=4    13,14,15,16=7   17,19,21,23=8   25,27,29,31=9 |
                                                   16                              |
                                                   0                               |
                                                   6                               |
                                                   11                              |
          4          8               12                            24              |
          6          6.1             6.2                           11              |
          1          3               4                             8               |
                                     7                             9               |
1,2,3,4=1  5,6,7,8=3    9,10,11,12=4    13,14,15=7   17,19,21,23=8   25,27,29,31=9 |
                                                16                              |
                                                0                               |
                                                6                               |
                                                11                              |
          4          8               12                         24              |
          6          6.1             6.2                        11              |
          1          3               4                          8               |
                                     7                          9               |
1,2,3,4=1  5,6,7,8=3    9,10,11,12=4    13,15=7   17,19,21,23=8   25,27,29,31=9 |
                                             16                              |
                                             0                               |
                                             6                               |
                                             11                              |
          4          8            12                         24              |
          6          6.1          6.2                        11              |
          1          3            4                          8               |
                                  7                          9               |
1,2,3,4=1  5,6,7,8=3    9,10,11=4    13,15=7   17,19,21,23=8   25,27,29,31=9 |
                                     16                              |
                                     0                               |
                                     6                               |
                                     11                              |
          4          8                               24              |
          6          6.1                             11              |
          1          3                               8               |
                     4                               9               |
1,2,3,4=1  5,6,7,8=3    9,11,13,15=4   17,19,21,23=8   25,27,29,31=9 |
                                   16                              |
                                   0                               |
                                   6                               |
                                   11                              |
          4        8                               24              |
          6        6.1                             11              |
          1        3                               8               |
                   4                               9               |
1,2,3,4=1  5,6,7=3    9,11,13,15=4   17,19,21,23=8   25,27,29,31=9 |
                                 16                              |
                                 0                               |
                                 6                               |
                                 11                              |
          4      8                               24              |
          6      6.1                             11              |
          1      3                               8               |
                 4                               9               |
1,2,3,4=1  5,7=3    9,11,13,15=4   17,19,21,23=8   25,27,29,31=9 |
                               16                              |
                               0                               |
                               6                               |
                               11                              |
        4      8                               24              |
        6      6.1                             11              |
        1      3                               8               |
               4                               9               |
1,2,3=1  5,7=3    9,11,13,15=4   17,19,21,23=8   25,27,29,31=9 |
                        16                              |
                        0                               |
                        6                               |
                        11                              |
          8                             24              |
          6                             11              |
          1                             8               |
          4                             9               |
1,3,5,7=1  9,11,13,15=4   17,19,21,23=8   25,27,29,31=9 |
        8             16               24               |
        0             0.1              0.2              |
        1             4                8                |
                                       9                |
3,5,7=1  9,11,13,15=4    17,19,21,23=8    25,27,29,31=9 |
      8             16               24               |
      0             0.1              0.2              |
      1             4                8                |
                                     9                |
5,7=1  9,11,13,15=4    17,19,21,23=8    25,27,29,31=9 |
    8             16               24               |
    0             0.1              0.2              |
    1             4                8                |
                                   9                |
7=1  9,11,13,15=4    17,19,21,23=8    25,27,29,31=9 |
             16              24               |
             0               0.1              |
             1               8                |
                             9                |
9,11,13,15=1   17,19,21,23=8    25,27,29,31=9 |
           16              24               |
           0               0.1              |
           1               8                |
                           9                |
11,13,15=1   17,19,21,23=8    25,27,29,31=9 |
        16              24               |
        0               0.1              |
        1               8                |
                        9                |
13,15=1   17,19,21,23=8    25,27,29,31=9 |
     16              24               |
     0               0.1              |
     1               8                |
                     9                |
15=1   17,19,21,23=8    25,27,29,31=9 |
              24              |
              0               |
              1               |
              9               |
17,19,21,23=1   25,27,29,31=9 |
           24              |
           0               |
           1               |
           9               |
19,21,23=1   25,27,29,31=9 |
        24              |
        0               |
        1               |
        9               |
21,23=1   25,27,29,31=9 |
     24              |
     0               |
     1               |
     9               |
23=1   25,27,29,31=9 |
25,27,29,31=0 |
27,29,31=0 |
29,31=0 |
31=0 |
=0 |
""");
   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_create();
    test_leaf();
    test_allocFree();
    test_btree();
    test_find();
    test_findAndInsert();
    test_isLeaf();
    test_splitLeafRoot();
    test_splitBranchRoot();
    test_splitLeafNotTop();
    test_splitLeafAtTop();
    test_splitBranchNotTop();
    test_splitBranchAtTop();
    test_isRootLeafFull();
    test_put();
    test_putReverse();
    test_putRandom();
    test_mergeLeavesIntoRoot();
    test_mergeLeavesAtTop();
    test_mergeLeavesNotTop();
    test_mergeBranchesIntoRoot();
    test_mergeBranchesAtTop();
    test_mergeBranchesNotTop();
    test_merge();
    test_delete();
   }

  static void newTests()                                                        // Tests being worked on
   {oldTests();
   }

  public static void main(String[] args)                                        // Test if called as a program
   {try                                                                         // Get a traceback in a format clickable in Geany if something goes wrong to speed up debugging.
     {if (github_actions) oldTests(); else newTests();                          // Tests to run
      if (github_actions)                                                       // Coverage analysis
       {coverageAnalysis(sourceFileName(), 12);
       }
      testSummary();                                                            // Summarize test results
      System.exit(testsFailed);
     }
    catch(Exception e)                                                          // Get a traceback in a format clickable in Geany
     {System.err.println(e);
      System.err.println(fullTraceBack(e));
      System.exit(1);
     }
   }
 }
