//------------------------------------------------------------------------------
// Btree using block memory
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2025
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Btree in a block on the surface of a silicon chip.
// Reduce b.btreeIndex to b.index or better make a class BtreeIndex
import java.util.*;

class Btree extends Test                                                        // Manipulate a btree in a block of memory
 {final int size;                                                               // The maximum number of nodes == branches or leaves in the btree
  final int stuckSize;                                                          // The maximum number of entries in the stuck.
  final int bitsPerKey;                                                         // The number of bits needed to define a key
  final int bitsPerData;                                                        // The number of bits needed to define a data field
  final Layout L;                                                               // Layout of the stuck
  final Layout.Field freeStart;                                                 // Start of free chain. Initially all stucks are on the free chain except the root stuck
  final Layout.Field stuckIsLeaf;                                               // Whether the current stuck is acting as a leaf or a branch in the btree.
  final Layout.Field stuckIsFree;                                               // Whether the stuck is on the free chain
  final Layout.Field freeNext;                                                  // Next stuck on the free chain. If this stuck is not on the free chain then this field is zero to show that this stuck in use
  final Layout.Field stuckSizeField;                                            // Current size of stuck up to the maximum size
  final Layout.Field stuckKeys;                                                 // Keys field
  final Layout.Field stuckData;                                                 // Data field
  static boolean debug = false;                                                 // Debug if enabled

//D1 Construction                                                               // Construct and layout a btree

  Btree(int Size, int StuckSize, int BitsPerKey, int BitsPerData)               // Create the Btree
   {if (StuckSize % 2 == 1) stop("The stuck size must be even, not:", StuckSize);
    if (StuckSize < 4)      stop("The stuck size must be greater than equal to 4, not:", StuckSize);
    size             = Size;                                                    // The maximum number of entries in the btree.
    stuckSize        = StuckSize;                                               // The maximum number of entries in the stuck.
    bitsPerKey       = BitsPerKey;                                              // The number of bits needed to define a key
    bitsPerData      = BitsPerData;                                             // The number of bits needed to define a data field
    L                = layout();                                                // Layout of the btree
    freeStart   = L.locateFieldByName("freeStart");                             // Start of free chain. Initially all sticks are on the free chain except the root stuck
    stuckIsLeaf = L.locateFieldByName("stuckIsLeaf");                           // Whether the stuck is a leaf
    stuckIsFree = L.locateFieldByName("stuckIsFree");                           // Whether the stuck is on the free chain
    freeNext    = L.locateFieldByName("freeNext");                              // Next element refernce on free chain
    stuckSizeField   = L.locateFieldByName("stuckSize");                        // Current size of stuck up to the maximum size
    stuckKeys   = L.locateFieldByName("stuckKeys");                             // Keys field
    stuckData   = L.locateFieldByName("stuckData");                             // Data field

    createFreeChain();                                                          // Create the free chain
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
""", logTwo(size)+1, size, logTwo(size)+1, logTwo(stuckSize)+1, stuckSize, bitsPerKey, bitsPerData));
   }

  Layout.Field variable(String name, int size)                                  // Create a variable
   {final Layout.Field v = L.variable(name, size);
    v.layout.P = L.P;
    return v;
   }

  Layout.Field bit(String name) {return variable(name, 1);}                     // Create a bit
  Layout.Field btreeIndex()     {return variable("btreeIndex", logTwo(size)+1);}// Create an index for a stuck in a btree
  Layout.Field isLeaf()         {return variable("isLeaf", 1);}                 // Create a bit for is full

  void runProgram()                      {L.runProgram();}
  void clearProgram()                    {L.clearProgram();}
  void stopProgram(String message)       {L.stopProgram(message);}
  Layout.Program startNewProgram()       {return L.startNewProgram();}
  void continueProgram(Layout.Program p) {L.continueProgram(p);}

//D2 Allocation                                                                 // Allocate stucks from the free chain

  void createFreeChain()                                                        // Create the free chain
   {final Layout.Field index = btreeIndex();
    final Layout.Program p = L.startNewProgram();
    freeStart.iWrite(1);
    for (int i = 1; i < size; i++)
     {final int I = i;
      L.P.new Instruction()
      {void action()
        {index.value = I;
         freeNext.value = I+1 == size ? 0 : I+1;
         stuckIsFree.value = 1;
        }
      };
      freeNext   .iWrite(index);
      stuckIsFree.iWrite(index);
     }
    setRootAsLeaf();
    L.runProgram();
    L.continueProgram(p);
   }

  private void allocate(Layout.Field ref)                                       // Allocate a stuck and set a ref to the allocated node
   {final Layout.Field btreeIndex = btreeIndex();
    L.P.new Instruction()
     {void action()
       {if (freeStart.value == 0)                                               // Check memory
         {stopProgram("Out of memory");
          return;
         }
        ref.value = btreeIndex.value = freeStart.value;                         // Head of free chain gives allocated stuck
       }
     };

    stuckIsFree.iZero(btreeIndex);                                              // Show as in use
    freeNext.iRead(btreeIndex);                                                 // Locate next stuck on free chain to become new first stuck on free chain

    L.P.new Instruction()
     {void action()
       {freeStart.value = freeNext.value;                                       // Next stuck on free chain becomes head of free chain
       }
     };
    freeNext.iZero(btreeIndex);                                                 // Clear the next field from the current stuck
   }

  private void allocateLeaf(Layout.Field ref)                                   // Allocate a stuck, set a ref to the allocated node and mark it a leaf
   {allocate(ref);
    setLeaf(ref);
   }

  private void allocateBranch(Layout.Field ref)                                 // Allocate a stuck, set a ref to the allocated node and mark it a branch
   {allocate(ref);
    setBranch(ref);
   }

  private void free(Layout.Field ref)                                           // Free the indicated stuck to make it available for reuse
   {L.P.new Instruction()
     {void action()
       {if (ref.value == 0)                                                     // The root stuck cannot be freed
         {stopProgram("Cannot free the root stuck");
          return;
         }
       }
     };
    L.P.new Instruction()
     {void action()
       {freeNext.value = freeStart.value;
       }
     };

    freeNext.iWrite(ref);                                                       // Append the free chain to this stuck
    L.P.new Instruction()
     {void action()
       {freeStart.value = ref.value;                                            // This stuck becomes the first stuick on the free chain
       }
     };
    stuckIsFree.iOne(ref);                                                      // Show as free
   }

//D2 Stuck                                                                      // Get and set stucks within btree

  Stuck stuck()                                                                 // Make a temporary stuck we can copy into or out of as needed
   {final Stuck s = new Stuck(stuckSize, bitsPerKey, bitsPerData);
    s.L.P = L.P;
    return s;
   }

  void copyStuckFrom(Stuck S, Layout.Field BtreeIndex)                          // Copy a stuck out of the btree
   {stuckSizeField.iRead(BtreeIndex);
    S.stuckSize.iMove(stuckSizeField);

    Layout.Field index = S.index();

    for (int i = 0; i < S.size; i++)
     {index.iWrite(i);
      stuckKeys.iRead(BtreeIndex, index); S.stuckKeys.iMove(stuckKeys); S.stuckKeys.iWrite(index);
      stuckData.iRead(BtreeIndex, index); S.stuckData.iMove(stuckData); S.stuckData.iWrite(index);
     }
   }

  void saveStuckInto(Stuck S, Layout.Field BtreeIndex)                          // Save a stuck into the indicated position in the btree
   {stuckSizeField.iMove(S.stuckSize);                                          // Get the size field from the btree
    stuckSizeField.iWrite(BtreeIndex);                                          // Set the size field in the stuck

    Layout.Field index = S.index();

    for (int i = 0; i < S.size; i++)
     {final int I = i;
      index.iWrite(I);
      S.stuckKeys.iRead(index); stuckKeys.iMove(S.stuckKeys); stuckKeys.iWrite(BtreeIndex, index);
      S.stuckData.iRead(index); stuckData.iMove(S.stuckData); stuckData.iWrite(BtreeIndex, index);
     }
   }

  void copyStuckFromRoot(Stuck S)                                               // Copy a stuck out of the root of the btree
   {final Layout.Field i = btreeIndex();
    i.iWrite(0);
    copyStuckFrom(S, i);
   }

  void saveStuckIntoRoot(Stuck S)                                               // Copy a stuck out of the root of the btree
   {final Layout.Field i = btreeIndex();
    i.iWrite(0);
    saveStuckInto(S, i);
   }

//D1 Attributes                                                                 // Get and set attributes

  void setRootAsLeaf()                                                          // Set the root to be a leaf
   {final Layout.Field i = btreeIndex();
    i.iWrite(0);
    stuckIsLeaf.iOne(i);
   }

  void setRootAsBranch()                                                        // Set the root to be a branch
   {final Layout.Field i = btreeIndex();
    i.iWrite(0);
    stuckIsLeaf.iZero(i);
   }

  void setLeaf  (Layout.Field i) {stuckIsLeaf.iOne (i);}                        // Set a stuck in the btree to be a leaf
  void setBranch(Layout.Field i) {stuckIsLeaf.iZero(i);}                        // Set a stuck in the btree to be a branch

  void isLeaf(Layout.Field index, Layout.Field isLeaf)                          // Is leaf at indicated index
   {stuckIsLeaf.iRead(index);
    isLeaf.iMove(stuckIsLeaf);
   }

  void isRootLeaf(Layout.Field isLeaf)                                          // Is the root a leaf
   {final Layout.Field i = btreeIndex();
    i.iZero();
    stuckIsLeaf.iRead(i);
    isLeaf.iMove(stuckIsLeaf);
   }

  void isRootLeafFull(Layout.Field isFull)                                      // Is the root assumed to be a leaf full?
   {final Layout.Field i = btreeIndex();
    i.iZero();
    stuckSizeField.iRead(i);
    L.P.new Instruction()
     {void action()
       {isFull.value = stuckSizeField.value >= stuckSize ? 1 : 0;
       }
     };
   }

  void isRootBranchFull(Layout.Field isFullButOne)                              // Is the root assumed to be a root full?
   {final Layout.Field i = btreeIndex();
    i.iZero();
    stuckSizeField.iRead(i);
    L.P.new Instruction()
     {void action()
       {isFullButOne.value = stuckSizeField.value >= stuckSize-1 ? 1 : 0;
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
      Layout.Field btreeIndex = btreeIndex(); btreeIndex.value = index;

      final Layout.Program p = L.startNewProgram();
      stuckIsLeaf.iRead(btreeIndex);
      stuckIsFree.iRead(btreeIndex);
      freeNext   .iRead(btreeIndex);
      copyStuckFrom(stuck, btreeIndex);
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
    final Layout.Field btreeIndex = btreeIndex();
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
   {final int N = level * linesToPrintABranch + stuckSize;                      // Number of lines we might want
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
    final Layout.Field isFull = bit("isFull");
    final Layout.Field cl = btreeIndex(), cr = btreeIndex();                    // Indexes of left and right children
    final Layout.Field pl = p.key(),      pr = p.key(), plr = p.key();          // Parent key must be smaller than anything in right child yet greater than or equal to anything in the left child

    copyStuckFromRoot(p);                                                       // Load leaf root stuck from btree

    p.isFull(isFull);                                                           // Check whether the leaf root stuck is full
    L.P.new If(isFull)
     {void Else()                                                               // The leaf root stuck is not full so it cannot be split
       {L.P.new Instruction()
         {void action()
           {L.P.stopProgram("A root leaf must be full before it can be split");
           }
         };
       }
     };

    p.splitIntoTwo(l, r, stuckSize / 2);                                        // Split the leaf root in two down the middle
    allocateLeaf(cl); saveStuckInto(l, cl);                                     // Allocate and save left leaf
    allocateLeaf(cr); saveStuckInto(r, cr);                                     // Allocate and save right leaf
                                                                                // Update root with new children
    l.lastElement();  pl.iMove(l.stuckKeys);                                    // Last element of left child
    r.firstElement(); pr.iMove(r.stuckKeys);                                    // First element of right child
    plr.iAdd(pl, pr); plr.iHalf();                                              // Mid point key

    p.clear();                                                                  // Clear the root so we can add the left and right children to it.
    p.stuckKeys.iMove(plr); p.stuckData.iMove(cl); p.push();                    // Add reference to left child
                            p.stuckData.iMove(cr); p.setPastLastElement();      // Add reference to right child
    saveStuckIntoRoot(p); setRootAsBranch();                                    // Save the root stuck back into the btree and mark it as a branch
   }

  private void splitRootBranch()                                                // Split a full root branch
   {final Stuck p = stuck(), l = stuck(), r = stuck();                          // Parent == root, left, right stucks
    final Layout.Field isFullButOne = bit("isFullButOne");
    final Layout.Field           cl = btreeIndex(), cr = btreeIndex();          // Indexes of left and right children
    final int              midPoint = (stuckSize-1) / 2;                        // Mid point in parent

    copyStuckFromRoot(p);                                                       // Load branch root stuck from btree

    p.isFullButOne(isFullButOne);                                               // Check whether the branch root stuck is full
    L.P.new If(isFullButOne)
     {void Else()                                                               // The branch root stuck is not full so it cannot be split
       {L.P.new Instruction()
         {void action()
           {L.P.stopProgram("A root branch must be full before it can be split");
           }
         };
       }
     };

    p.splitIntoThree(l, r, midPoint);                                           // Split the branch root in two down the middle
    allocateBranch(cl); saveStuckInto(l, cl);                                   // Allocate and save left branch
    allocateBranch(cr); saveStuckInto(r, cr);                                   // Allocate and save right branch
                                                                                // Update root with new children
    p.stuckKeys.iRead(midPoint);                                                // Get splitting key
    p.stuckData.iMove(cl);                                                      // Refence to left child stuck

    p.clear();                                                                  // Clear the root so we can add the left and right children to it.
    p.push();                                                                   // Add reference to left child
    p.stuckData.iMove(cr); p.setPastLastElement();                              // Add reference to right child as top element past the end of the stuck
    saveStuckIntoRoot(p);                                                       // Save the root stuck back into the btree and mark it as a branch
   }

  private void splitLeafNotTop(Layout.Field parentIndex, Layout.Field stuckIndex) // Split a full leaf that is not the root and is not the last child of its parent branch which is not full
   {final Stuck p = stuck(), c = stuck(), l = stuck(), r = stuck();             // Parent which must be a branch which is not full, child at index which must be a full leaf, left and right splits of leaf
    final Layout.Field isFull = bit("isFull"), isFullButOne = bit("isFullButOne");
    final Layout.Field isLeaf = bit("isLeaf");
    final Layout.Field ci = btreeIndex(), cl = btreeIndex(), cr = btreeIndex(); // Btree indexes of child and left and right children of child
    final Layout.Field ck = p.key(), pl = p.key(), pr = p.key(), plr = p.key(); // Key of child in parent, splitting key which must be smaller than anything in right child of child yet greater than or equal to anything in the left child of child

    copyStuckFrom(p, parentIndex);                                              // Load parent stuck from btree
    p.stuckKeys.iRead(stuckIndex); ck.iMove(p.stuckKeys);                       // Key of child
    p.stuckData.iRead(stuckIndex); cr.iMove(p.stuckData);                       // Reference to child
    copyStuckFrom(c, p.stuckData);                                              // Load child

    isLeaf(parentIndex, isLeaf);                                                // The parent stuck must be a branch
    L.P.new If(isLeaf)
     {void Then()
       {L.P.new Instruction()
         {void action()
           {L.P.stopProgram("Parent must be a branch");
           }
         };
       }
     };

    p.isFullButOne(isFullButOne);                                               // The parent stuck may not be full
    L.P.new If(isFullButOne)
     {void Then()
       {L.P.new Instruction()
         {void action()
           {L.P.stopProgram("Parent must not be full");
           }
         };
       }
     };

    isLeaf(cr, isLeaf);                                                         // The child stuck must be a leaf
    L.P.new If(isLeaf)
     {void Else()
       {L.P.new Instruction()
         {void action()
           {L.P.stopProgram("Child must be a leaf");
           }
         };
       }
     };

    c.isFull(isFull);                                                           // The child stuck must be a leaf
    L.P.new If(isFull)
     {void Else()
       {L.P.new Instruction()
         {void action()
           {L.P.stopProgram("Child leaf must be full");
           }
         };
       }
     };

    c.splitLow(l, stuckSize / 2);                                               // Split the leaf in two down the middle copying out the lower half
    allocateLeaf(cl); saveStuckInto(l, cl);                                     // Allocate and save left leaf
                      saveStuckInto(c, cr);                                     // Allocate and save left leaf
                                                                                // Update root with new children
    l.lastElement();   pl.iMove(l.stuckKeys);                                   // Last element of left child
    c.firstElement();  pr.iMove(c.stuckKeys);                                   // First element of right child
    plr.iAdd(pl, pr); plr.iHalf();                                              // Mid point key

    p.stuckKeys.iMove(plr); p.stuckData.iMove(cl);
    p.insertElementAt(stuckIndex);                                              // Add reference to left child
    saveStuckInto(p, parentIndex);                                              // Save the parent stuck back into the btree
   }

  private void splitLeafAtTop(Layout.Field parentIndex)                         // Split a full leaf that is not the root and is the last child of its parent branch which is not full
   {final Stuck p = stuck(), c = stuck(), l = stuck(), r = stuck();             // Parent which must be a branch which is not full, child at index which must be a full leaf, left and right splits of leaf
    final Layout.Field isFull       = bit("isFull");
    final Layout.Field isFullButOne = bit("isFullButOne");
    final Layout.Field isLeaf       = bit("isLeaf");
    final Layout.Field ci = btreeIndex(), cl = btreeIndex(), cr = btreeIndex(); // Btree indexes of child and left and right children of child
    final Layout.Field ck = p.key(), pl = p.key(), pr = p.key(), plr = p.key(); // Key of child in parent, splitting key which must be smaller than anything in right child of child yet greater than or equal to anything in the left child of child

    copyStuckFrom(p, parentIndex);                                              // Load parent stuck from btree
    p.pastLastElement();                                                        // Key of child
    cr.iMove(p.stuckData);                                                      // Reference to child in btree
    copyStuckFrom(c, p.stuckData);                                              // Load child from btree

    isLeaf(parentIndex, isLeaf);                                                // The parent stuck must be a branch
    L.P.new If(isLeaf)
     {void Then()
       {L.P.new Instruction()
         {void action()
           {L.P.stopProgram("Parent must be a branch");
           }
         };
       }
     };

    p.isFullButOne(isFullButOne);                                               // The parent stuck may not be full
    L.P.new If(isFullButOne)
     {void Then()
       {L.P.new Instruction()
         {void action()
           {L.P.stopProgram("Parent must not be full");
           }
         };
       }
     };

    isLeaf(cr, isLeaf);                                                         // The child stuck must be a leaf
    L.P.new If(isLeaf)
     {void Else()
       {L.P.new Instruction()
         {void action()
           {L.P.stopProgram("Child must be a leaf");
           }
         };
       }
     };

    c.isFull(isFull);                                                           // The child stuck must be a leaf
    L.P.new If(isFull)
     {void Else()
       {L.P.new Instruction()
         {void action()
           {L.P.stopProgram("Child leaf must be full");
           }
         };
       }
     };

    c.splitLow(l, stuckSize / 2);                                               // Split the leaf in two down the middle copying out the lower half
    allocateLeaf(cl); saveStuckInto(l, cl);                                     // Allocate and save left leaf
                      saveStuckInto(c, cr);                                     // Allocate and save left leaf
                                                                                // Update root with new children
    l.lastElement();   pl.iMove(l.stuckKeys);                                   // Last element of left child
    c.firstElement();  pr.iMove(c.stuckKeys);                                   // First element of right child
    plr.iAdd(pl, pr); plr.iHalf();                                              // Mid point key

    p.stuckKeys.iMove(plr); p.stuckData.iMove(cl); p.push();                    // Add reference to left child
    p.stuckKeys.iZero();    p.stuckData.iMove(cr); p.setPastLastElement();      // Add reference to not split top child on the right
    saveStuckInto(p, parentIndex);                                              // Save the parent stuck back into the btree
   }

  private void splitBranchNotTop                                                // Split a full branch that is not the root and is not the last child of its parent branch which is not full
   (Layout.Field parentIndex, Layout.Field stuckIndex)
   {final Stuck p = stuck(), c = stuck(), l = stuck(), r = stuck();             // Parent which must be a branch which is not full, child at index which must be a full leaf, left and right splits of leaf
    final Layout.Field isFull       = bit("isFull");
    final Layout.Field isFullButOne = bit("isFullButOne");
    final Layout.Field isLeaf       = bit("isLeaf");
    final Layout.Field ci  = btreeIndex(), cl = btreeIndex(), cr = btreeIndex();// Btree indexes of child and left and right children of child
    final Layout.Field ck  = p.key(), pl = p.key(), pr = p.key(), plr = p.key();// Key of child in parent, splitting key which must be smaller than anything in right child of child yet greater than or equal to anything in the left child of child
    final Layout.Field key = p.key();                                           // The central key

    copyStuckFrom(p, parentIndex);                                              // Load parent stuck from btree
    p.stuckKeys.iRead(stuckIndex); ck.iMove(p.stuckKeys);                       // Key of child
    p.stuckData.iRead(stuckIndex); cr.iMove(p.stuckData);                       // Reference to child
    copyStuckFrom(c, p.stuckData);                                              // Load child

    isLeaf(parentIndex, isLeaf);                                                // The parent stuck must be a branch
    L.P.new If(isLeaf)
     {void Then()
       {L.P.new Instruction()
         {void action()
           {L.P.stopProgram("Parent must be a branch");
           }
         };
       }
     };

    p.isFullButOne(isFullButOne);                                               // The parent stuck may not be full
    L.P.new If(isFullButOne)
     {void Then()
       {L.P.new Instruction()
         {void action()
           {L.P.stopProgram("Parent must not be full");
           }
         };
       }
     };

    isLeaf(cr, isLeaf);                                                         // The child stuck must be a branch
    L.P.new If(isLeaf)
     {void Then()
       {L.P.new Instruction()
         {void action()
           {L.P.stopProgram("Child must be a branch");
           }
         };
       }
     };

    c.isFullButOne(isFullButOne);                                               // The child stuck must be a leaf
    L.P.new If(isFullButOne)
     {void Else()
       {L.P.new Instruction()
         {void action()
           {L.P.stopProgram("Child branch must be full");
           }
         };
       }
     };

    c.splitLowButOne(l, (stuckSize-1) / 2, key);                                // Split the leaf in two down the middle copying out the lower half

    allocateBranch(cl); saveStuckInto(l, cl);                                   // Allocate and save left leaf
                        saveStuckInto(c, cr);                                   // Allocate and save left leaf
                                                                                // Update root with new children
    p.stuckKeys.iMove(key); p.stuckData.iMove(cl);
    p.insertElementAt(stuckIndex);                                              // Add reference to left child
    saveStuckInto(p, parentIndex);                                              // Save the parent stuck back into the btree
   }

  private void splitBranchAtTop(Layout.Field parentIndex)                       // Split a full leaf that is not the root and is the last child of its parent branch which is not full
   {final Stuck p = stuck(), c = stuck(), l = stuck(), r = stuck();             // Parent which must be a branch which is not full, child at index which must be a full leaf, left and right splits of leaf
    final Layout.Field isFull = bit("isFull");
    final Layout.Field isFullButOne = bit("isFullButOne");
    final Layout.Field isLeaf = bit("isLeaf");
    final Layout.Field ci = btreeIndex(), cl = btreeIndex(), cr = btreeIndex(); // Btree indexes of child and left and right children of child
    final Layout.Field ck = p.key(), pl = p.key(), pr = p.key(), plr = p.key(); // Key of child in parent, splitting key which must be smaller than anything in right child of child yet greater than or equal to anything in the left child of child
    final Layout.Field center = p.key();                                        // The central key

    copyStuckFrom(p, parentIndex);                                              // Load parent stuck from btree
    p.pastLastElement();                                                        // Key of child
    cr.iMove(p.stuckData);                                                      // Reference to child in btree
    copyStuckFrom(c, p.stuckData);                                              // Load child from btree

    isLeaf(parentIndex, isLeaf);                                                // The parent stuck must be a branch
    L.P.new If(isLeaf)
     {void Then()
       {L.P.new Instruction()
         {void action()
           {L.P.stopProgram("Parent must be a branch");
           }
         };
       }
     };

    p.isFullButOne(isFullButOne);                                               // The parent stuck may not be full
    L.P.new If(isFullButOne)
     {void Then()
       {L.P.new Instruction()
         {void action()
           {L.P.stopProgram("Parent must not be full");
           }
         };
       }
     };

    isLeaf(cr, isLeaf);                                                         // The child stuck must be a leaf
    L.P.new If(isLeaf)
     {void Then()
       {L.P.new Instruction()
         {void action()
           {L.P.stopProgram("Child must be a branch");
           }
         };
       }
     };

    c.isFullButOne(isFullButOne);                                               // The child stuck must be a leaf
    L.P.new If(isFullButOne)
     {void Else()
       {L.P.new Instruction()
         {void action()
           {L.P.stopProgram("Child branch must be full");
           }
         };
       }
     };

    c.splitLowButOne(l, (stuckSize-1) / 2, center);                             // Split the leaf in two down the middle copying out the lower half
    allocateBranch(cl); saveStuckInto(l, cl);                                   // Allocate and save left leaf
                        saveStuckInto(c, cr);                                   // Allocate and save left leaf
                                                                                // Update root with new children
    p.stuckKeys.iMove(center); p.stuckData.iMove(cl); p.push();                 // Add reference to left child
    p.stuckKeys.iZero();       p.stuckData.iMove(cr); p.setPastLastElement();   // Add reference to not split top child on the right
    saveStuckInto(p, parentIndex);                                              // Save the parent stuck back into the btree
   }

//D1 Merge                                                                      // Merge two nodes

  private void mergeLeavesIntoRoot()                                            // Merge two leaves into the root
   {final Stuck p = stuck(), l = stuck(), r = stuck();                          // Root and left, right children
    final Layout.Field li  = btreeIndex(), ri = btreeIndex();                   // Btree indexes of left and right children of root

    copyStuckFromRoot(p);                                                       // Load root
    L.P.new Block()
     {void code()
       {L.P.new Instruction()                                                   // Check that the root has one entry and thus two children
         {void action()
           {if (p.stuckSize.value != 1) L.P.Goto(end);                          // Wrong number of entries in root
           };
         };
        p.stuckData.iRead(0); li.iMove(p.stuckData);                            // Index of left leaf
        p.stuckData.iRead(1); ri.iMove(p.stuckData);                            // Index of right leaf
        new IsLeaf(li)                                                          // Check that the children are leaves
         {void Branch()                                                         // Children are not leaves
           {L.P.iGoto(end);
           }
         };
        copyStuckFrom(l, li);                                                   // Load left  leaf from btree
        copyStuckFrom(r, ri);                                                   // Load right leaf from btree
        p.merge(l, r);                                                          // Merge leaves into root
        saveStuckIntoRoot(p);                                                   // Save the modified root back into the tree
        setRootAsLeaf();                                                        // Set the root to be a leaf
        free(li); free(ri);                                                     // Free left and right leaves as they are no longer needed
       }
     };
   }

  private void mergeLeavesNotTop(Layout.Field Parent, Layout.Field LeftLeaf)    // Merge the two consecutive leaves of a branch that is not the root. Neither of the leaves is the topmost leaf.
   {final Stuck p = stuck(), l = stuck(), r  = stuck();                         // Parent, left and right children
    final Layout.Field ls = p.index(),    rs = p.index();                       // Indices in stuck of left and right children
    final Layout.Field li = btreeIndex(), ri = btreeIndex();                    // Btree indexes of left and right children of parent that we want to merge
    copyStuckFrom(p, Parent);                                                   // Load parent

    L.P.new Block()
     {void code()
       {L.P.new Instruction()                                                   // Check that the parent has a child at the specified index
         {void action()
           {if (p.stuckSize.value < 2)
             {L.P.stopProgram("Parent must have at least two entries");
             }
           };
         };

        p.stuckData.iRead    (LeftLeaf); li.iMove(p.stuckData);                 // Get the btree index of the left child leaf
        p.stuckData.iReadNext(LeftLeaf); ri.iMove(p.stuckData);                 // Get the btree index of the right child leaf

        new IsLeaf(li)                                                          // Check that the children are leaves
         {void Branch()                                                         // Children are not leaves
           {L.P.iGoto(end);
           }
         };
        copyStuckFrom(l, li);                                                   // Load left  leaf from btree
        copyStuckFrom(r, ri);                                                   // Load right leaf from btree
        l.merge(r);                                                             // Merge leaves into left child

        p.removeElementAt(LeftLeaf);                                            // Remove the left child
        p.stuckData.iMove(li); p.setDataAt(LeftLeaf);                           // Replace the right child with the left child
        saveStuckInto(l, li);                                                   // Save the modified left child back into the tree
        saveStuckInto(p, Parent);                                               // Save the modified root back into the tree
        free(ri);                                                               // Free right leaf as it is no longer in use
       }
     };
   }

  private void mergeLeavesAtTop(Layout.Field Parent)                            // Merge the top most two leaves of a branch that is not the root
   {final Stuck p = stuck(), l = stuck(), r  = stuck();                         // Parent, left and right children
    final Layout.Field ls = p.index(),    rs = p.index();                       // Indices in stuck of left and right children
    final Layout.Field li = btreeIndex(), ri = btreeIndex();                    // Btree indexes of left and right children of parent that we want to merge

    copyStuckFrom(p, Parent);                                                   // Load parent

    L.P.new Block()
     {void code()
       {L.P.new Instruction()                                                   // Check that the parent has a child at the specified index
         {void action()
           {if (p.stuckSize.value == 0)
             {L.P.stopProgram("Parent must have at least one entry and hence two children for a merge");
             }
           };
         };

        ls.iMove(p.stuckSize); ls.iDec();                                       // Index of left leaf known to be valid as the parent contains at least one entry resulting in two children
        rs.iMove(p.stuckSize);                                                  // Index of right leaf
        p.stuckData.iRead(ls); li.iMove(p.stuckData);                           // Get the btree index of the left child leaf
        p.stuckData.iRead(rs); ri.iMove(p.stuckData);                           // Get the btree index of the right child leaf

        new IsLeaf(li)                                                          // Check that the children are leaves
         {void Branch()                                                         // Children are not leaves
           {L.P.iGoto(end);
           }
         };
        copyStuckFrom(l, li);                                                   // Load left  leaf from btree
        copyStuckFrom(r, ri);                                                   // Load right leaf from btree
        l.merge(r);                                                             // Merge leaves into left child
        p.stuckSize.iDec();                                                     // The left child is now topmost - we know this is ok because the parent has at elast one entry
        saveStuckInto(l, li);                                                  // Save the modified left child back into the tree
        saveStuckInto(p, Parent);                                               // Save the modified root back into the tree
        free(ri);                                                               // Free right leaf as it is no longer in use
       }
     };
   }

  private void mergeBranchesIntoRoot()                                          // Merge two branches into the root
   {final Stuck p = stuck(), l = stuck(), r = stuck();                          // Root and left, right children
    final Layout.Field li  = btreeIndex(), ri = btreeIndex();                   // Btree indexes of left and right children of root
    final Layout.Field k   = p.key();                                           // Splitting key

    copyStuckFromRoot(p);                                                       // Load root
    L.P.new Block()
     {void code()
       {L.P.new Instruction()                                                   // Check that the root has one entry and thus two children
         {void action()
           {if (p.stuckSize.value != 1) L.P.Goto(end);
           };
         };
        p.stuckKeys.iRead(0); k .iMove(p.stuckKeys);                            // Splitting key
        p.stuckData.iRead(0); li.iMove(p.stuckData);                            // Index of left branch
        p.stuckData.iRead(1); ri.iMove(p.stuckData);                            // Index of right branch
        new IsLeaf(li)                                                          // Check that the children are leaves
         {void Leaf()                                                           // Children are not leaves
           {L.P.iGoto(end);
           }
         };
        copyStuckFrom(l, li);                                                   // Load left  branch from btree
        copyStuckFrom(r, ri);                                                   // Load right branch from btree
        p.mergeButOne(l, k, r);                                                 // Merge left branch, splitting key, right branch into root
        saveStuckIntoRoot(p);                                                   // Save the modified root back into the tree
        free(li); free(ri);                                                     // Free left and right leaves as they are no longer needed
       }
     };
   }

  private void mergeBranchesNotTop(Layout.Field Parent, Layout.Field LeftBranch)// Merge the two consecutive child branches of a branch that is not the root. Neither of the child branches is the topmost leaf.
   {final Stuck p = stuck(), l = stuck(), r  = stuck();                         // Parent, left and right children
    final Layout.Field ls = p.index(),    rs = p.index();                       // Indices in stuck of left and right children
    final Layout.Field li = btreeIndex(), ri = btreeIndex();                    // Btree indexes of left and right children of parent that we want to merge
    copyStuckFrom(p, Parent);                                                   // Load parent

    L.P.new Block()
     {void code()
       {L.P.new Instruction()                                                   // Check that the parent has a child at the specified index
         {void action()
           {if (p.stuckSize.value < 2)
             {L.P.stopProgram("Parent must have at least two entries");
             }
           };
         };

        p.stuckData.iRead    (LeftBranch); li.iMove(p.stuckData);               // Get the btree index of the left child branch
        p.stuckData.iReadNext(LeftBranch); ri.iMove(p.stuckData);               // Get the btree index of the right child branch

        new IsLeaf(li)                                                          // Check that the children are branches
         {void Leaf()                                                           // Children are not branches
           {L.P.iGoto(end);
           }
         };
        copyStuckFrom(l, li);                                                   // Load left  branch from btree
        copyStuckFrom(r, ri);                                                   // Load right branch from btree
        p.stuckKeys.iRead(LeftBranch);                                          // Key associated with left child branch
        l.mergeButOne(p.stuckKeys, r);                                          // Merge branches into left child
        p.removeElementAt(LeftBranch);                                          // Remove the left child
        p.stuckData.iMove(li); p.setDataAt(LeftBranch);                         // Replace the right child with the left child
        saveStuckInto(l, li);                                                   // Save the modified left child back into the tree
        saveStuckInto(p, Parent);                                               // Save the modified root back into the tree
        free(ri);                                                               // Free right branch as it is no longer in use
       }
     };
   }

  private void mergeBranchesAtTop(Layout.Field Parent)                          // Merge the top most two child branches of a branch that is not the root
   {final Stuck p = stuck(), l = stuck(), r  = stuck();                         // Parent, left and right children
    final Layout.Field ls = p.index(),    rs = p.index();                       // Indices in stuck of left and right children
    final Layout.Field li = btreeIndex(), ri = btreeIndex();                    // Btree indexes of left and right children of parent that we want to merge
    copyStuckFrom(p, Parent);                                                   // Load parent

    L.P.new Block()
     {void code()
       {L.P.new Instruction()                                                   // Check that the parent has a child at the specified index
         {void action()
           {if (p.stuckSize.value == 0)
             {L.P.stopProgram("Parent must have at least one entry and hence two children for a merge");
             }
           };
         };

        ls.iMove(p.stuckSize); ls.iDec();                                       // Index of left branch known to be valid as the parent contains at least one entry resulting in two children
        rs.iMove(p.stuckSize);                                                  // Index of right branch
        p.stuckData.iRead(ls); li.iMove(p.stuckData);                           // Get the btree index of the left branch branch
        p.stuckData.iRead(rs); ri.iMove(p.stuckData);                           // Get the btree index of the right branch branch

        new IsLeaf(li)                                                          // Check that the children are branches
         {void Leaf()                                                           // Children are branches
           {L.P.iGoto(end);
           }
         };
        copyStuckFrom(l, li);                                                   // Load left  branch from btree
        copyStuckFrom(r, ri);                                                   // Load right branch from btree
        p.pop();                                                                // Key associated with left child branch
        l.mergeButOne(p.stuckKeys, r);                                          // Merge leaves into left child
        p.stuckData.iMove(li);                                                  // Index of left branch that now contains the combined branches
        p.setPastLastData();                                                    // Make newly combined left branch top most
        saveStuckInto(l, li);                                                   // Save the modified left child back into the tree
        saveStuckInto(p, Parent);                                               // Save the modified root back into the tree
        free(ri);                                                               // Free right branch as it is no longer in use
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
    Layout.Field Data, Layout.Field btreeIndex, Layout.Field stuckIndex)
   {final Stuck        S = stuck();
    final Layout.Field s = btreeIndex;
    s.iZero();                                                                  // Start at the root
    L.P.new Block()
     {void code()
       {copyStuckFrom(S, s);                                                    // Set search key
        S.stuckKeys.iMove(Key);
        new IsLeaf(s)
         {void Leaf()                                                           // At a leaf - search for exact match
           {S.search_eq(Found, stuckIndex);                                     // Search
            L.P.iGoZero(end, Found);                                             // Key not present
            S.elementAt(stuckIndex);                                            // Look up data
            Data.iMove(S.stuckData);                                            // Save data
            L.P.Goto  (end);                                                    // Successfully found the key
           }
          void Branch()                                                         // On a branch - step to next level down
           {S.search_le(Found, stuckIndex);                                     // Search stuck for matching key
            s.iMove(S.stuckData);                                               // Index of next stuck down
            L.P.iGoto(start);                                                    // Key not present
           }
         };
       };
     };
   }

//D1 Insertion                                                                  // Insert a key, data pair into the tree if ther is room for it or update and existing key with a new datum

  private void findAndInsert(Layout.Field Found)                                // Find the leaf that should contain this key and insert or update it is possible setting Found to true if found else to false indocatying that the key, data pair still needs to be inserted
   {final Stuck  S          = stuck();
    Layout.Field Key        = S.key();
    Layout.Field Data       = S.data();
    Layout.Field btreeIndex = btreeIndex();
    Layout.Field stuckIndex = S.index();
    Layout.Field empty      = S.empty();
    Layout.Field full       = S.full();

    L.P.new Block()
     {void code()
       {Key .iMove(stuckKeys);
        Data.iMove(stuckData);
        find(Key, Found, Data, btreeIndex, stuckIndex);                         // Find the leaf that should contain the key and possibly the key.
        copyStuckFrom(S, btreeIndex);                                           // Copy the stuck that should contain the key
        S.stuckKeys.iMove(Key);
        S.stuckData.iMove(Data);
        L.P.new If (Found)                                                      // Found the key in the leaf so update it with the new data
         {void Then()
           {S.setElementAt(stuckIndex);
            saveStuckInto(S, btreeIndex);
            Found.iOne();
            L.P.iGoto(end);
           }
         };

        S.isFull(full);                                                         // Check whether the stuck is full
        L.P.new If (full)
         {void Else()                                                           // Leaf is not full so we can insert immediately
           {S.search_le(Found, stuckIndex);
            S.stuckKeys.iMove(Key);
            S.stuckData.iMove(Data);
            L.P.new If(Found)
             {void Then() {S.insertElementAt(stuckIndex);}
              void Else() {S.push();}
             };
            saveStuckInto(S, btreeIndex);
            Found.iOne();
            L.P.iGoto(end);
           }
         };
        Found.iZero();                                                          // The key has not been inserted
       }
     };
   }

  public void put()                                                             // Insert a key, data pair into the tree or update and existing key with a new datum
   {final Stuck        S            = stuck();
    final Layout.Field p            = btreeIndex();                             // Previous or parent position in the btree
    final Layout.Field s            = btreeIndex();                             // Current position in the btree
    final Layout.Field Key          = S.key();
    final Layout.Field Data         = S.data();
    final Layout.Field btreeIndex   = btreeIndex();
    final Layout.Field stuckIndex   = S.index();
    final Layout.Field empty        = S.empty();
    final Layout.Field full         = S.full();
    final Layout.Field found        = S.found();
    final Layout.Field isLeaf       = bit("isLeaf");
    final Layout.Field fullButOne   = S.fullButOne();

    Key.iMove(stuckKeys); Data.iMove(stuckData);

    L.P.new Block()
     {void code()
       {stuckKeys.iMove(Key);
        stuckData.iMove(Data);
        findAndInsert(found);    // hand target label in directly               // Try direct insertion with no modifications to the shape of the tree
        L.P.iGoNotZero(end, found);                                              // Direct insertion succeeded
        isRootLeaf(isLeaf);                                                     // Failed to insert because the root is a leaf and must therefore be full
        L.P.new If (isLeaf)                                                     // Root is a leaf
         {void Then()
           {splitRootLeaf();                                                    // Split the leaf root to make room
            stuckKeys.iMove(Key); stuckData.iMove(Data);                        // Key, data pair to be inserted
            findAndInsert(found);                                               // Splitting a leaf root will make more space in the tree
            L.P.iGoto(end);                                                      // Direct insertion succeeded
           }
         };
        isRootBranchFull(fullButOne);                                           // Root is a full branch so split it
        L.P.new If (fullButOne)
         {void Then()
           {splitRootBranch();                                                  // Split the branch root to make room
            L.P.iGoto(start);                                                    // Restart descent to make sure we are on the right path
           }
         };

        s.iZero(); p.iZero();                                                   // Start at the root and step down through the tree to the key splitting as we go
        copyStuckFrom(S, s);                                                    // Load root

        L.P.new Block()
         {void code()
           {S.stuckKeys.iMove(Key);

            S.search_le(found, stuckIndex);                                     // Step down
            p.iMove(s);                                                         // Parent
            s.iMove(S.stuckData);                                               // Child
            copyStuckFrom(S, s);                                                // Load child

            new IsLeaf(s)                                                       // Child is a leaf or a branch
             {void Leaf()                                                       // At a leaf - search for exact match
               {S.isFull(full);

                L.P.new If (full)
                 {void Then()                                                   // Child branch is full
                   {L.P.new If (found)
                     {void Then()
                       {splitLeafNotTop(p, stuckIndex);                         // Split the child leaf known not to be top
                       }
                      void Else()
                       {splitLeafAtTop(p);                                      // Split the child leaf known to be top
                       }
                     };
                   }
                 };
                stuckKeys.iMove(Key); stuckData.iMove(Data);                    // Key, data pair to be inserted
                findAndInsert(found);                                           // Must be insertable now necuase we have split everything in the path of the key
                L.P.Goto  (end);                                                // Successfully found the key
               }
              void Branch()                                                     // Child is a branch
               {S.isFullButOne(fullButOne);
                L.P.new If (fullButOne)
                 {void Then()                                                   // Child branch is full
                   {L.P.new If (found)
                     {void Then()
                       {splitBranchNotTop(p, stuckIndex);                       // Split the child branch known not to be top
                       }
                      void Else()
                       {splitBranchAtTop(p);                                    // Split the child branch known to be top
                       }
                     };
                    s.iMove(p);                                                 // Restart at the parent so we enter the child stuck that contains the key
                    copyStuckFrom(S, s);                                        // Reload stuck so we start again at the parent level
                   }
                 };
                L.P.iGoto(start);                                                // Try again
               }
             };
           };
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
    final Layout.Field btreeIndex = b.btreeIndex();
    final Layout.Field a          = b.variable("a", 4);

    btreeIndex.iWrite(0);
    b.new IsLeaf(btreeIndex)
     {void Leaf  () {a.iWrite(1);}
      void Branch() {a.iWrite(2);}
     };
    b.runProgram();
    ok(a, "a: value=1");
   }

  static void test_allocFree()
   {final Btree b = test_create();
    final Layout.Field x = b.btreeIndex();
    final Layout.Field y = b.btreeIndex();

    ok(b.freeStart, "freeStart: value=1");
    ok(b.freeNext,  "freeNext: value=0, 0=0, 1=2, 2=3, 3=4, 4=5, 5=6, 6=7, 7=8, 8=9, 9=10, 10=11, 11=12, 12=13, 13=14, 14=15, 15=16, 16=17, 17=18, 18=19, 19=20, 20=21, 21=22, 22=23, 23=24, 24=25, 25=26, 26=27, 27=28, 28=29, 29=30, 30=31, 31=0");
    ok(b.dump(), """
Btree
Stuck:  0   size: 0   free: 0   next:  0  leaf: 1
stuckSize: value=0
stuckKeys: value=0, 0=0, 1=0, 2=0, 3=0
stuckData: value=0, 0=0, 1=0, 2=0, 3=0
""");

    b.allocate(x);
    b.allocate(y);
    b.runProgram();

    ok(x, "btreeIndex: value=1");
    ok(y, "btreeIndex: value=2");

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
    b.free(x);
    b.free(y);
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
    final Layout.Field s = b.btreeIndex();
    final Layout.Field t = b.btreeIndex();
    final Layout.Field x = b.btreeIndex();
    final Layout.Field y = b.btreeIndex();

    final Stuck S = b.stuck();
    final Stuck T = b.stuck();
    final Stuck X = b.stuck();
    final Stuck Y = b.stuck();
    final Stuck Z = b.stuck();
    b.allocate(s);
    b.allocate(t);
    b.allocate(x);
    b.allocate(y);
    b.runProgram();

    b.clearProgram();
    S.stuckKeys.iWrite( 1); S.stuckData.iWrite( 2); S.push();
    S.stuckKeys.iWrite( 2); S.stuckData.iWrite( 4); S.push();
    S.stuckKeys.iWrite( 3); S.stuckData.iWrite( 6); S.push();
    S.stuckKeys.iWrite( 4); S.stuckData.iWrite( 8); S.push();
    b.runProgram();

    b.clearProgram();
    T.stuckKeys.iWrite(11); T.stuckData.iWrite(12); T.push();
    T.stuckKeys.iWrite(12); T.stuckData.iWrite(14); T.push();
    T.stuckKeys.iWrite(13); T.stuckData.iWrite(16); T.push();
    T.stuckKeys.iWrite(14); T.stuckData.iWrite(18); T.push();
    b.runProgram();

    b.clearProgram();
    X.stuckKeys.iWrite(21); X.stuckData.iWrite(22); X.push();
    X.stuckKeys.iWrite(22); X.stuckData.iWrite(24); X.push();
    X.stuckKeys.iWrite(23); X.stuckData.iWrite(26); X.push();
    X.stuckKeys.iWrite(24); X.stuckData.iWrite(28); X.push();
    b.runProgram();

    b.clearProgram();
    Y.stuckKeys.iWrite(31); Y.stuckData.iWrite(32); Y.push();
    Y.stuckKeys.iWrite(32); Y.stuckData.iWrite(34); Y.push();
    Y.stuckKeys.iWrite(33); Y.stuckData.iWrite(36); Y.push();
    Y.stuckKeys.iWrite(34); Y.stuckData.iWrite(38); Y.push();
    b.runProgram();

    b.clearProgram();
    Z.stuckKeys.iWrite(10); Z.stuckData.iWrite(s.value); Z.push();
    Z.stuckKeys.iWrite(20); Z.stuckData.iWrite(t.value); Z.push();
    Z.stuckKeys.iWrite(30); Z.stuckData.iWrite(x.value); Z.push();
    Z.stuckKeys.iWrite(0);  Z.stuckData.iWrite(y.value); Z.setPastLastElement();
    b.runProgram();

    b.clearProgram();
    b.saveStuckInto(S, s);   b.setLeaf(s);
    b.saveStuckInto(T, t);   b.setLeaf(t);
    b.saveStuckInto(X, x);   b.setLeaf(x);
    b.saveStuckInto(Y, y);   b.setLeaf(y);
    b.saveStuckIntoRoot(Z);  b.setRootAsBranch();
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
    final Layout.Field btreeIndex = b.btreeIndex();

    b.L.P.maxSteps = 500;

    b.clearProgram();
    Key.iWrite(5);
    b.find(Key, Found, Data, btreeIndex, stuckIndex);
    b.runProgram();

    //stop(Found);
    ok(Found, "found: value=0");

    b.clearProgram();
    Key.iWrite(4);
    b.find(Key, Found, Data, btreeIndex, stuckIndex);
    b.runProgram();

    //stop(Found, Data, btreeIndex, stuckIndex);
    ok(Found, "found: value=1");
    ok(Data,  "data: value=8");
    ok(btreeIndex, "btreeIndex: value=1");
    ok(stuckIndex, "stuckIndex: value=3");

    b.clearProgram();
    Key.iWrite(14);
    b.find(Key, Found, Data, btreeIndex, stuckIndex);
    b.runProgram();

    //stop(Found, Data, btreeIndex, stuckIndex);
    ok(Found, "found: value=1");
    ok(Data,  "data: value=18");
    ok(btreeIndex, "btreeIndex: value=2");
    ok(stuckIndex, "stuckIndex: value=3");

    b.clearProgram();
    Key.iWrite(23);
    b.find(Key, Found, Data, btreeIndex, stuckIndex);
    b.runProgram();

    //stop(Found, Data, btreeIndex, stuckIndex);
    ok(Found, "found: value=1");
    ok(Data,  "data: value=26");
    ok(btreeIndex, "btreeIndex: value=3");
    ok(stuckIndex, "stuckIndex: value=2");

    b.clearProgram();
    Key.iWrite(32);
    b.find(Key, Found, Data, btreeIndex, stuckIndex);
    b.runProgram();

    //stop(Found, Data, btreeIndex, stuckIndex);
    ok(Found, "found: value=1");
    ok(Data,  "data: value=34");
    ok(btreeIndex, "btreeIndex: value=4");
    ok(stuckIndex, "stuckIndex: value=1");
   }

  static Btree test_findAndInsert()
   {final Btree b = test_create();
    final Layout.Field Found = b.bit("Found");

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

    Layout.Field index = b.btreeIndex();
    b.clearProgram();
    b.allocateLeaf(index);
    b.setRootAsBranch();
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
    final Layout.Field isLeaf     = b.bit("isLeaf");
    final Layout.Field btreeIndex = b.btreeIndex();

    b.clearProgram();
    b.isRootLeaf(isLeaf);
    b.runProgram();
    ok(isLeaf, "isLeaf: value=0");

    b.clearProgram();
    btreeIndex.iZero();
    b.isLeaf(btreeIndex, isLeaf);
    b.runProgram();
    ok(isLeaf, "isLeaf: value=0");

    b.clearProgram();
    btreeIndex.iOne();
    b.isLeaf(btreeIndex, isLeaf);
    b.runProgram();
    ok(isLeaf, "isLeaf: value=1");
   }

  static void test_splitLeafRoot()
   {final Btree b = test_create();
    final Layout.Field Found = b.bit("Found");

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
    b.splitRootLeaf();
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
    b.splitRootBranch();
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
    final Layout.Field Found = b.bit("Found");
    final Layout.Field R     = b.btreeIndex();
    final Layout.Field I     = r.index();
    final Layout.Field L     = b.btreeIndex();

    b.L.P.maxSteps = 500;

    b.clearProgram(); r.stuckKeys.iWrite(10); r.stuckData.iWrite(1); r.push();               b.runProgram();
    b.clearProgram(); r.stuckKeys.iWrite(20); r.stuckData.iWrite(0); r.push();               b.runProgram();
    b.clearProgram(); r.stuckKeys.iWrite(30); r.stuckData.iWrite(0); r.setPastLastElement(); b.runProgram();
    b.clearProgram(); b.saveStuckIntoRoot(r);                                                b.runProgram();

    b.clearProgram(); l.stuckKeys.iWrite(1); l.stuckData.iWrite(1); l.push(); b.runProgram();
    b.clearProgram(); l.stuckKeys.iWrite(2); l.stuckData.iWrite(2); l.push(); b.runProgram();
    b.clearProgram(); l.stuckKeys.iWrite(3); l.stuckData.iWrite(3); l.push(); b.runProgram();
    b.clearProgram(); l.stuckKeys.iWrite(4); l.stuckData.iWrite(4); l.push(); b.runProgram();

    b.clearProgram();
    b.saveStuckIntoRoot(r);                     b.setRootAsBranch();
    b.allocateLeaf(L); b.saveStuckInto(l, L);   b.setLeaf(L);
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
    b.splitLeafNotTop(R, I);
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
    final Layout.Field Found = b.bit("Found");
    final Layout.Field R     = b.btreeIndex();
    final Layout.Field L     = b.btreeIndex();

    b.L.P.maxSteps = 500;

    b.clearProgram(); r.stuckKeys.iWrite(10); r.stuckData.iWrite(0); r.push();               b.runProgram();
    b.clearProgram(); r.stuckKeys.iWrite(20); r.stuckData.iWrite(0); r.push();               b.runProgram();
    b.clearProgram(); r.stuckKeys.iWrite(30); r.stuckData.iWrite(1); r.setPastLastElement(); b.runProgram();
    b.clearProgram(); b.saveStuckIntoRoot(r);                                                b.runProgram();

    b.clearProgram(); l.stuckKeys.iWrite(1); l.stuckData.iWrite(1); l.push(); b.runProgram();
    b.clearProgram(); l.stuckKeys.iWrite(2); l.stuckData.iWrite(2); l.push(); b.runProgram();
    b.clearProgram(); l.stuckKeys.iWrite(3); l.stuckData.iWrite(3); l.push(); b.runProgram();
    b.clearProgram(); l.stuckKeys.iWrite(4); l.stuckData.iWrite(4); l.push(); b.runProgram();

    b.clearProgram();
    b.saveStuckIntoRoot(r);                       b.setRootAsBranch();
    b.allocateBranch(L); b.saveStuckInto(l, L);   b.setLeaf(L);
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
    b.splitLeafAtTop(R);
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
    final Layout.Field Found = b.bit("Found");
    final Layout.Field R     = b.btreeIndex();
    final Layout.Field I     = r.index();
    final Layout.Field L     = b.btreeIndex();

    b.L.P.maxSteps = 500;

    b.clearProgram(); r.stuckKeys.iWrite(10); r.stuckData.iWrite(1); r.push();               b.runProgram();
    b.clearProgram(); r.stuckKeys.iWrite(20); r.stuckData.iWrite(0); r.push();               b.runProgram();
    b.clearProgram(); r.stuckKeys.iWrite(30); r.stuckData.iWrite(0); r.setPastLastElement(); b.runProgram();
    b.clearProgram(); b.saveStuckIntoRoot(r);                                                b.runProgram();

    b.clearProgram(); l.stuckKeys.iWrite(1); l.stuckData.iWrite(2); l.push(); b.runProgram();
    b.clearProgram(); l.stuckKeys.iWrite(2); l.stuckData.iWrite(3); l.push(); b.runProgram();
    b.clearProgram(); l.stuckKeys.iWrite(3); l.stuckData.iWrite(4); l.push(); b.runProgram();
    b.clearProgram(); l.stuckKeys.iWrite(4); l.stuckData.iWrite(5); l.setPastLastElement(); b.runProgram();

    b.clearProgram();
    b.saveStuckIntoRoot(r);                     b.setRootAsBranch();
    b.allocateBranch(L); b.saveStuckInto(l, L); b.setBranch(L);
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
    b.splitBranchNotTop(L, I);
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
    final Layout.Field Found = b.bit("Found");
    final Layout.Field R     = b.btreeIndex();
    final Layout.Field L     = b.btreeIndex();

    b.L.P.maxSteps = 500;

    b.clearProgram(); r.stuckKeys.iWrite(10); r.stuckData.iWrite(0); r.push();               b.runProgram();
    b.clearProgram(); r.stuckKeys.iWrite(20); r.stuckData.iWrite(0); r.push();               b.runProgram();
    b.clearProgram(); r.stuckKeys.iWrite(30); r.stuckData.iWrite(1); r.setPastLastElement(); b.runProgram();
    b.clearProgram(); b.saveStuckIntoRoot(r);                                                b.runProgram();

    b.clearProgram(); l.stuckKeys.iWrite(1); l.stuckData.iWrite(1); l.push();                b.runProgram();
    b.clearProgram(); l.stuckKeys.iWrite(2); l.stuckData.iWrite(2); l.push();                b.runProgram();
    b.clearProgram(); l.stuckKeys.iWrite(3); l.stuckData.iWrite(3); l.push();                b.runProgram();
    b.clearProgram(); l.stuckKeys.iWrite(4); l.stuckData.iWrite(4); l.setPastLastElement();  b.runProgram();

    b.clearProgram();
    b.saveStuckIntoRoot(r);                       b.setRootAsBranch();
    b.allocateBranch(L); b.saveStuckInto(l, L);   b.setBranch(L);
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
    b.splitBranchAtTop(R);
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
    final Layout.Field f = b.bit("leafFull");
    final Layout.Field F = b.bit("branchFull");

    b.clearProgram(); b.stuckKeys.iWrite(10); b.stuckData.iWrite(11); b.findAndInsert(f); b.isRootLeafFull(f); b.isRootBranchFull(F); b.runProgram(); ok(f, "leafFull: value=0"); ok(F, "branchFull: value=0");
    b.clearProgram(); b.stuckKeys.iWrite(20); b.stuckData.iWrite(21); b.findAndInsert(f); b.isRootLeafFull(f); b.isRootBranchFull(F); b.runProgram(); ok(f, "leafFull: value=0"); ok(F, "branchFull: value=0");
    b.clearProgram(); b.stuckKeys.iWrite(30); b.stuckData.iWrite(31); b.findAndInsert(f); b.isRootLeafFull(f); b.isRootBranchFull(F); b.runProgram(); ok(f, "leafFull: value=0"); ok(F, "branchFull: value=1");
    b.clearProgram(); b.stuckKeys.iWrite(40); b.stuckData.iWrite(41); b.findAndInsert(f); b.isRootLeafFull(f); b.isRootBranchFull(F); b.runProgram(); ok(f, "leafFull: value=1"); ok(F, "branchFull: value=1");
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
                                                                            16                                        24                                       |
                                                                            0                                         0.1                                      |
                                                                            22                                        14                                       |
                                                                                                                      15                                       |
                               8                    12                                            20                                       28                  |
                               22                   22.1                                          14                                       15                  |
                               24                   20                                            12                                       5                   |
                                                    17                                            9                                        6                   |
           4        6                    10                      14                    18                   22                   26                  30        |
           24       24.1                 20                      17                    12                   9                    5                   6         |
           25       23                   19                      16                    11                   8                    4                   1         |
                    21                   18                      13                    10                   7                    3                   2         |
1,2,3,4=25   5,6=23     7,8=21   9,10=19   11,12=18     13,14=16   15,16=13   17,18=11   19,20=10   21,22=8   23,24=7    25,26=4   27,28=3   29,30=1   31,32=2 |
""");
   }

  static void test_putRandom()
   {final Btree b = new Btree(64, 4, 16, 16);

    b.L.P.maxSteps = 2000;

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
                                                                                                                                                                                                                                                                                                                                                    528                                                                                                                                                                                                                                                                  |
                                                                                                                                                                                                                                                                                                                                                    0                                                                                                                                                                                                                                                                    |
                                                                                                                                                                                                                                                                                                                                                    38                                                                                                                                                                                                                                                                   |
                                                                                                                                                                                                                                                                                                                                                    39                                                                                                                                                                                                                                                                   |
                                                                                                                                253                                                                                          379                                                                                                                                                                                                                                        718                                                                                                                                              |
                                                                                                                                38                                                                                           38.1                                                                                                                                                                                                                                       39                                                                                                                                               |
                                                                                                                                50                                                                                           29                                                                                                                                                                                                                                         36                                                                                                                                               |
                                                                                                                                                                                                                             16                                                                                                                                                                                                                                         17                                                                                                                                               |
                                                               143                                                                                                             298                                                                            429                                                   497                                                                    582                                                                                                                                                    894                                                                    |
                                                               50                                                                                                              29                                                                             16                                                    16.1                                                                   36                                                                                                                                                     17                                                                     |
                                                               40                                                                                                              44                                                                             21                                                    48                                                                     27                                                                                                                                                     31                                                                     |
                                                               25                                                                                                              14                                                                                                                                   5                                                                      11                                                                                                                                                     6                                                                      |
              34               87               104                          156               210                235                         266               283                          341           356                              402                                 440          457                                   506                                    568                                    630                   672                688                             805           819                    856                                  909                   946               988          |
              40               40.1             40.2                         25                25.1               25.2                        44                44.1                         14            14.1                             21                                  48           48.1                                  5                                      27                                     11                    11.1               11.2                            31            31.1                   31.2                                 6                     6.1               6.2          |
              41               24               51                           53                26                 42                          52                33                           34            45                               15                                  49           8                                     22                                     18                                     28                    13                 46                              32            47                     23                                   43                    12                37           |
                                                35                                                                9                                             19                                         1                                20                                               30                                    3                                      7                                                                               4                                                                    10                                                                           2            |
1,13,27,29=41   39,43,55,72=24     90,96,103=51     106,135=35    151,155=53    157,186,188=26     229,232,234=42     237,246=9    260,261=52    272,273,279=33     288,298=19    317,338=34    344,354=45     358,376,377=1     391,401=15    403,422,425=20    436,437,438=49    442,447=8     472,480,490,494=30     501,503=22    511,516,526=3    545,554,560,564=18    576,577,578=7    586,611,612,615=28    650,657,658,667=13     679,681,686=46     690,704=4    769,773,804=32    806,809=47     826,830,839,854=23     858,882,884=10    903,906,907=43    912,922,937,946=12    961,976,987=37    989,993=2 |
""");
   }

  static void test_mergeLeavesIntoRoot()
   {final Btree b = test_create();
    final Stuck s = b.stuck();
    final Layout.Field index = b.btreeIndex();
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
    b.copyStuckFrom(s, index);
    s.pop(); s.pop();
    b.saveStuckInto(s, index);
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
    b.mergeLeavesIntoRoot();
    b.runProgram();
    //stop(b);
    ok(b, """
1,2,3,4=0 |
""");
   }

  static void test_mergeLeavesNotTop()
   {final Btree b = test_create();
    final Stuck s = b.stuck();
    final Layout.Field btreeIndex = b.btreeIndex();
    final Layout.Field stuckIndex = s.index();
    b.L.P.maxSteps = 2000;

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
    btreeIndex.value = 0;
    stuckIndex.value = 0;
    b.clearProgram();
    b.mergeLeavesNotTop(btreeIndex, stuckIndex);
    b.runProgram();
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
    final Layout.Field index = b.btreeIndex();
    b.L.P.maxSteps = 2000;

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
    b.copyStuckFrom(s, index);
    s.pop(); s.pop();
    b.saveStuckInto(s, index);
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
    b.mergeLeavesAtTop(index);
    b.runProgram();
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
    final Layout.Field index = b.btreeIndex();
    b.L.P.maxSteps = 2000;

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
    b.copyStuckFrom(s, index);
    s.pop();
    b.saveStuckInto(s, index);
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
    b.mergeBranchesIntoRoot();
    b.runProgram();
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
    final Layout.Field btreeIndex = b.btreeIndex();
    final Layout.Field stuckIndex = s.index();
    b.L.P.maxSteps = 2000;

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
    btreeIndex.value = 0;
    stuckIndex.value = 0;
    b.clearProgram();
    b.mergeBranchesNotTop(btreeIndex, stuckIndex);
    b.runProgram();
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
    final Layout.Field index = b.btreeIndex();
    b.L.P.maxSteps = 2000;

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
    b.copyStuckFrom(s, index);
    s.pop();
    b.saveStuckInto(s, index);
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
    b.mergeBranchesAtTop(index);
    b.runProgram();
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
