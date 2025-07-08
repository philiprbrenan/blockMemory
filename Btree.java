//------------------------------------------------------------------------------
// Btree using block memory
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2025
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Btree in a block on the surface of a silicon chip.
//Remove field from ends of names
//Remove unused variables and subs
//Improve size of stucks tests in split* methods

import java.util.*;

class Btree extends Test                                                        // Manipulate a btree in a block of memory
 {final int size;                                                               // The maximum number of nodes == branches or leaves in the btree
  final int stuckSize;                                                          // The maximum number of entries in the stuck.
  final int bitsPerKey;                                                         // The number of bits needed to define a key
  final int bitsPerData;                                                        // The number of bits needed to define a data field
  final Layout L;                                                               // Layout of the stuck
  final Layout.Field freeStartField;                                            // Start of free chain. Initially all stucks are on the free chain except the root stuck
  final Layout.Field stuckIsLeafField;                                          // Whether the current stuck is acting as a leaf or a branch in the btree.
  final Layout.Field stuckIsFreeField;                                          // Whether the stuck is on the free chain
  final Layout.Field freeNextField;                                             // Next stuck on the free chain. If this stuck is not on the free chain then this field is zero to show that this stuck in use
  final Layout.Field stuckSizeField;                                            // Current size of stuck up to the maximum size
  final Layout.Field stuckKeysField;                                            // Keys field
  final Layout.Field stuckDataField;                                            // Data field

//D1 Construction                                                               // Construct and layout a btree

  Btree(int Size, int StuckSize, int BitsPerKey, int BitsPerData)               // Create the Btree
   {if (StuckSize % 2 == 1) stop("The stuck size must be even, not:", StuckSize);
    if (StuckSize < 4)      stop("The stuck size must be greater than equal to 4, not:", StuckSize);
    size             = Size;                                                    // The maximum number of entries in the btree.
    stuckSize        = StuckSize;                                               // The maximum number of entries in the stuck.
    bitsPerKey       = BitsPerKey;                                              // The number of bits needed to define a key
    bitsPerData      = BitsPerData;                                             // The number of bits needed to define a data field
    L                = layout();                                                // Layout of the btree
    freeStartField   = L.locateFieldByName("freeStart");                        // Start of free chain. Initially all sticks are on the free chain except the root stuck
    stuckIsLeafField = L.locateFieldByName("stuckIsLeaf");                      // Whether the stuck is a leaf
    stuckIsFreeField = L.locateFieldByName("stuckIsFree");                      // Whether the stuck is on the free chain
    freeNextField    = L.locateFieldByName("freeNext");                         // Next element refernce on free chain
    stuckSizeField   = L.locateFieldByName("stuckSize");                        // Current size of stuck up to the maximum size
    stuckKeysField   = L.locateFieldByName("stuckKeys");                        // Keys field
    stuckDataField   = L.locateFieldByName("stuckData");                        // Data field

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

  void runProgram()                      {L.runProgram();}
  void clearProgram()                    {L.clearProgram();}
  void stopProgram(String message)       {L.stopProgram(message);}
  Layout.Program startNewProgram()       {return L.startNewProgram();}
  void continueProgram(Layout.Program p) {L.continueProgram(p);}

//D2 Allocation                                                                 // Allocate stucks from the free chain

  void createFreeChain()                                                        // Create the free chain
   {final Layout.Field index = btreeIndex();
    final Layout.Program p = L.startNewProgram();
    freeStartField.iWrite(1);
    for (int i = 1; i < size; i++)
     {final int I = i;
      L.P.new Instruction()
      {void action()
        {index.value = I;
         freeNextField.value = I+1;
         stuckIsFreeField.value = 1;
        }
      };
      freeNextField   .iWrite(index);
      stuckIsFreeField.iWrite(index);
     }
    setRootAsLeaf();
    L.runProgram();
    L.continueProgram(p);
   }

  private void allocate(Layout.Field ref)                                       // Allocate a stuck and set a ref to the allocated node
   {final Layout.Field btreeIndex = btreeIndex();
    L.P.new Instruction()
     {void action()
       {if (freeStartField.value == 0) stopProgram("Out of memory");            // Check memory
        ref.value = btreeIndex.value = freeStartField.value;                    // Head of free chain gives allocated stuck
       }
     };

    stuckIsFreeField.iZero(btreeIndex);                                         // Show as in use
    freeNextField.iRead(btreeIndex);                                            // Locate next stuck on free chain to become new first stuck on free chain

    L.P.new Instruction()
     {void action()
       {freeStartField.value = freeNextField.value;                             // Next stuck on free chain becomes head of free chain
       }
     };
    freeNextField.iZero(btreeIndex);                                            // Clear the next field from the current stuck
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
       {freeNextField.value = freeStartField.value;
       }
     };

    freeNextField.iWrite(ref);                                                  // Append the free chain to this stuck
    L.P.new Instruction()
     {void action()
       {freeStartField.value = ref.value;                                       // This stuck becomes the first stuick on the free chain
       }
     };
    stuckIsFreeField.iOne(ref);                                                 // Show as free
   }

//D2 Stuck                                                                      // Get and set stucks within btree

  Stuck stuck()                                                                 // Make a temporary stuck we can copy into or out of as needed
   {final Stuck s = new Stuck(stuckSize, bitsPerKey, bitsPerData);
    s.L.P = L.P;
    return s;
   }

  void copyStuckFrom(Stuck S, Layout.Field BtreeIndex)                          // Copy a stuck out of the btree
   {stuckSizeField.iRead(BtreeIndex);
    L.P.new Instruction()
     {void action()
       {S.stuckSize.value = stuckSizeField.value;
       }
     };

    Layout.Field index = S.index();

    for (int i = 0; i < S.size; i++)
     {index.iWrite(i);
      stuckKeysField.iRead(BtreeIndex, index); L.P.new Instruction() {void action() {S.stuckKeys.value = stuckKeysField.value;}}; S.stuckKeys.iWrite(index);
      stuckDataField.iRead(BtreeIndex, index); L.P.new Instruction() {void action() {S.stuckData.value = stuckDataField.value;}}; S.stuckData.iWrite(index);
     }
   }

  void saveStuckInto(Stuck S, Layout.Field BtreeIndex)                          // Save a stuck into the indicated position in the btree
   {stuckSizeField.iMove(S.stuckSize);                                           // Get the size field from the btree
    stuckSizeField.iWrite(BtreeIndex);                                   // Set the size field in the stuck

    Layout.Field index = S.index();

    for (int i = 0; i < S.size; i++)
     {final int I = i;
      index.iWrite(I);
      S.stuckKeys.iRead(index); L.P.new Instruction() {void action() {stuckKeysField.value = S.stuckKeys.value;}}; stuckKeysField.iWrite(BtreeIndex, index);
      S.stuckData.iRead(index); L.P.new Instruction() {void action() {stuckDataField.value = S.stuckData.value;}}; stuckDataField.iWrite(BtreeIndex, index);
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
    stuckIsLeafField.iOne(i);
   }

  void setRootAsBranch()                                                        // Set the root to be a branch
   {final Layout.Field i = btreeIndex();
    i.iWrite(0);
    stuckIsLeafField.iZero(i);
   }

  void setLeaf  (Layout.Field i) {stuckIsLeafField.iOne (i);}                   // Set a stuck in the btree to be a leaf
  void setBranch(Layout.Field i) {stuckIsLeafField.iZero(i);}                   // Set a stuck in the btree to be a branch

  void isLeaf(Layout.Field index, Layout.Field isLeaf)                          // Is leaf at indicated index
   {stuckIsLeafField.iRead(index);
    isLeaf.iMove(stuckIsLeafField);
   }

  void isRootLeaf(Layout.Field isLeaf)                                          // Is the root a leaf
   {final Layout.Field i = btreeIndex();
    i.iZero();
    stuckIsLeafField.iRead(i);
    isLeaf.iMove(stuckIsLeafField);
   }


//D1 Print                                                                      // Print the tree

  public String toString()
   {final StringBuilder s = new StringBuilder();
    final Stuck t = stuck();
    final Layout.Field btreeIndex = btreeIndex();
    final Layout.Field stuckIndex = t.index();
    s.append("Btree\n");
    for (int i = 0; i < size; i++)                                              // Each stuck in the btree
     {final Layout.Program p = L.startNewProgram();
      btreeIndex.iWrite(i);                                                     // Index the stuck
      stuckIsLeafField.iRead(btreeIndex);
      stuckIsFreeField.iRead(btreeIndex);
      freeNextField   .iRead(btreeIndex);
      copyStuckFrom(t, btreeIndex);                                             // Copy content of stuck in btree to a local stuck
      L.runProgram();
      L.continueProgram(p);
      if (stuckIsFreeField.value > 0) continue;                                 // Not in use as it is on the free chain

      s.append(String.format("Stuck: %2d   size: %d   free: %d   next: %2d  leaf: %d\n",
        i, t.stuckSize.value, freeNextField.value, freeNextField.value, stuckIsLeafField.value));
      s.append(""+t);
     }
    return ""+s;
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
     {void Else()                                                           // The leaf root stuck is not full so it cannot be split
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

    p.stuckKeys.iMove(plr); p.stuckData.iMove(cl); p.insertElementAt(stuckIndex);  // Add reference to left child
    saveStuckIntoRoot(p);                                                       // Save the parent stuck back into the btree
   }

  private void splitLeafAtTop(Layout.Field parentIndex)                         // Split a full leaf that is not the root and is the last child of its parent branch which is not full
   {final Stuck p = stuck(), c = stuck(), l = stuck(), r = stuck();             // Parent which must be a branch which is not full, child at index which must be a full leaf, left and right splits of leaf
    final Layout.Field isFull = bit("isFull"), isFullButOne = bit("isFullButOne");
    final Layout.Field isLeaf = bit("isLeaf");
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
    saveStuckIntoRoot(p);                                                       // Save the parent stuck back into the btree
   }

  private void splitBranchNotTop(Layout.Field parentIndex, Layout.Field stuckIndex)// Split a full branch that is not the root and is not the last child of its parent branch which is not full
   {final Stuck p = stuck(), c = stuck(), l = stuck(), r = stuck();             // Parent which must be a branch which is not full, child at index which must be a full leaf, left and right splits of leaf
    final Layout.Field isFull = bit("isFull"), isFullButOne = bit("isFullButOne");
    final Layout.Field isLeaf = bit("isLeaf");
    final Layout.Field ci = btreeIndex(), cl = btreeIndex(), cr = btreeIndex(); // Btree indexes of child and left and right children of child
    final Layout.Field ck = p.key(), pl = p.key(), pr = p.key(), plr = p.key(); // Key of child in parent, splitting key which must be smaller than anything in right child of child yet greater than or equal to anything in the left child of child
    final Layout.Field center = p.key();                                          // The central key

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

    c.splitLowButOne(l, (stuckSize-1) / 2, center);                             // Split the leaf in two down the middle copying out the lower half
    allocateLeaf(cl); saveStuckInto(l, cl);                                     // Allocate and save left leaf
                      saveStuckInto(c, cr);                                     // Allocate and save left leaf
                                                                                // Update root with new children
    p.stuckKeys.iMove(center); p.stuckData.iMove(cl); p.insertElementAt(stuckIndex);  // Add reference to left child
    saveStuckIntoRoot(p);                                                       // Save the parent stuck back into the btree
   }

  private void splitBranchAtTop(Layout.Field parentIndex)                       // Split a full leaf that is not the root and is the last child of its parent branch which is not full
   {final Stuck p = stuck(), c = stuck(), l = stuck(), r = stuck();             // Parent which must be a branch which is not full, child at index which must be a full leaf, left and right splits of leaf
    final Layout.Field isFull = bit("isFull"), isFullButOne = bit("isFullButOne");
    final Layout.Field isLeaf = bit("isLeaf");
    final Layout.Field ci = btreeIndex(), cl = btreeIndex(), cr = btreeIndex(); // Btree indexes of child and left and right children of child
    final Layout.Field ck = p.key(), pl = p.key(), pr = p.key(), plr = p.key(); // Key of child in parent, splitting key which must be smaller than anything in right child of child yet greater than or equal to anything in the left child of child
    final Layout.Field center = p.key();                                          // The central key

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

    c.isFullButOne(isFullButOne);                                                         // The child stuck must be a leaf
    L.P.new If(isFullButOne)
     {void Else()
       {L.P.new Instruction()
         {void action()
           {L.P.stopProgram("Child branch must be full");
           }
         };
       }
     };

    c.splitLowButOne(l, stuckSize / 2, center);                                 // Split the leaf in two down the middle copying out the lower half
    allocateLeaf(cl); saveStuckInto(l, cl);                                     // Allocate and save left leaf
                      saveStuckInto(c, cr);                                     // Allocate and save left leaf
                                                                                // Update root with new children
    p.stuckKeys.iMove(center); p.stuckData.iMove(cl); p.push();                 // Add reference to left child
    p.stuckKeys.iZero();       p.stuckData.iMove(cr); p.setPastLastElement();   // Add reference to not split top child on the right
    saveStuckIntoRoot(p);                                                       // Save the parent stuck back into the btree
   }

//D1 Find                                                                       // Find a key in a btree

  class IsLeaf                                                                  // Process a stuck depending on wnether it is a leaf or a branch
   {IsLeaf(Layout.Field index)
     {stuckIsLeafField.iRead(index);
      final IsLeaf  l = this;
      L.P.new If(stuckIsLeafField)
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
            L.P.GoZero(end, Found);                                             // Key not present
            S.elementAt(stuckIndex);                                            // Look up data
            Data.iMove(S.stuckData);                                            // Save data
            L.P.Goto  (end);                                                    // Successfully found the key
           }
          void Branch()                                                         // On a branch - step to next level down
           {S.search_le(Found, stuckIndex);                                     // Search stuck for matching key
            s.iMove(S.stuckData);                                               // Index of next stuck down
            L.P.Goto(start);                                                    // Key not present
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
       {Key .iMove(stuckKeysField);
        Data.iMove(stuckDataField);
        find(Key, Found, Data, btreeIndex, stuckIndex);                         // Find the leaf that should contain the key and possibly the key.
        copyStuckFrom(S, btreeIndex);                                           // Copy the stuck that should contain the key
        S.stuckKeys.iMove(Key);
        S.stuckData.iMove(Data);

        L.P.new If (Found)                                                      // Found the key in the leaf so update it with the new data
         {void Then()
           {S.setElementAt(stuckIndex);
            saveStuckInto(S, btreeIndex);
            Found.iOne();
            L.P.Goto(end);
           }
         };

        S.isFull(full);                                                         // Check whether the stuck is full
        L.P.new If (full)
         {void Else()                                                           // Leaf is not full so we can insert immediately
           {S.search_le(Found, stuckIndex);
            S.stuckKeys.iMove(Key);
            S.stuckData.iMove(Data);
            L.P.new If(Found)
             {void Then() {S.insertElementAt(stuckIndex);}                      //
              void Else() {S.push();}                                           //
             };
            saveStuckInto(S, btreeIndex);
            Found.iOne();
            L.P.Goto(end);
           }
         };
        Found.iZero();                                                          // The key has not been inserted
       }
     };
   }

  public void put()                                                             // Insert a key, data pair into the tree or update and existing key with a new datum
   {final Stuck  S           = stuck();
    Layout.Field Key         = S.key();
    Layout.Field Data        = S.data();
    Layout.Field btreeIndex  = btreeIndex();
    Layout.Field stuckIndex  = S.index();
    Layout.Field empty       = S.empty();
    Layout.Field full        = S.full();
    final Layout.Field found = S.found();

//    L.P.new Block()
//     {void code()
//       {findAndInsert(found);    // hand target label in directly               // Try direct insertion with no modifications to the shape of the tree
//        L.P.GoNotZero(end, found);                                              // Direct insertion succeeded
//        nT.loadRoot();                                                          // Load root
//        nT.isFull();
//        P.new If (T.at(isFull))                                                 // Start the insertion at the root(), after splitting it if necessary
//         {void Then()
//           {nT.isLeaf(T.at(IsLeaf));
//            P.new If (T.at(IsLeaf))
//             {void Then() {splitLeafRoot  ();}
//              void Else() {splitBranchRoot();}
//             };
//            z();
//            findAndInsert(Return);                                              // Splitting the root() might have been enough
//           }
//         };
//        nT.loadRootStuck(bT);                                                   // Load root as branch. If it were a leaf and had spae find and insert would have worked or the root would have been split and so must be branch.
//        T.at(parent).zero();
//
//        P.new Block()                                                           // Step down through the tree, splitting as we go
//         {void code()
//           {findFirstGreaterThanOrEqualInBranch                                 // Step down from parent to child
//             (nT, T.at(Key), null, T.at(first), T.at(child));
//
//            P.new Block()                                                       // Reached a leaf
//             {void code()
//               {nC.loadNode(T.at(child));
//                nC.isLeaf(T.at(IsLeaf));
//                P.GoOff(end, T.at(IsLeaf));
//                P.parallelStart();   tt(index,          first);                 // Index of the matching key
//                tt(node_splitLeaf, child);
//                tt(splitParent,   parent);
//
//
//                splitLeaf();                                                    // Split the child leaf
//                findAndInsert(null);                                            // Now guaranteed to work
//
//                merge();                                                        // Improve the tree along the path to the key
//                P.Goto(Return);
//               }
//             };
//            z();
//
//            nC.loadNode(T.at(child));
//            nC.branchSize(T.at(childSize));
//            T.at(childSize).equal(T.at(maxKeysPerBranch), T.at(branchIsFull));  // Check whether the child needs splitting because it is full
//
//            P.new If (T.at(branchIsFull))                                       // Step down, splitting full branches as we go
//             {void Then()
//               {P.parallelStart();   tt(index, first);
//                tt(node_splitBranch, child);
//                tt(splitParent, parent);
//
//
//                splitBranch();                                                  // Split the child branch in the search path for the key from the parent so the the search path does not contain a full branch above the containing leaf
//
//                findFirstGreaterThanOrEqualInBranch                             // Perform the step down again as the split will have altered the local layout
//                 (nT, T.at(Key), null, null, T.at(child));
//               }
//             };
//            nT.loadStuck(bT, child);                                            // Step down "From the heights"
//            tt(parent, child);
//            P.Goto(start);
//           }
//         };
//       }
//     };
   }

//D1 Tests                                                                      // Test the btree

  static Btree test_create()
   {final Btree b = new Btree(16, 4, 8, 8);
    ok(b, """
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

    ok(b.freeStartField, "freeStart: value=1");
    ok(b.freeNextField,  "freeNext: value=16, 0=0, 1=2, 2=3, 3=4, 4=5, 5=6, 6=7, 7=8, 8=9, 9=10, 10=11, 11=12, 12=13, 13=14, 14=15, 15=16");
    ok(b, """
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

    ok(b.freeStartField, "freeStart: value=3");
    ok(b.freeNextField,  "freeNext: value=0, 0=0, 1=0, 2=0, 3=4, 4=5, 5=6, 6=7, 7=8, 8=9, 9=10, 10=11, 11=12, 12=13, 13=14, 14=15, 15=16");

    ok(b, """
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
    ok(b.freeStartField, "freeStart: value=2");
    ok(b.freeNextField,  "freeNext: value=1, 0=0, 1=3, 2=1, 3=4, 4=5, 5=6, 6=7, 7=8, 8=9, 9=10, 10=11, 11=12, 12=13, 13=14, 14=15, 15=16");
    ok(b, """
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
//stop(b);
    ok(b, """
Btree
Stuck:  0   size: 3   free: 0   next:  0  leaf: 0
stuckSize: value=3
stuckKeys: value=0, 0=10, 1=20, 2=30, 3=0
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
    b.stuckKeysField.iWrite(20);
    b.stuckDataField.iWrite(21);
    b.findAndInsert(Found);
    b.runProgram();
    ok(b, """
Btree
Stuck:  0   size: 1   free: 0   next:  0  leaf: 1
stuckSize: value=1
stuckKeys: value=0, 0=20, 1=0, 2=0, 3=0
stuckData: value=0, 0=21, 1=0, 2=0, 3=0
""");

    b.clearProgram();
    b.stuckKeysField.iWrite(10);
    b.stuckDataField.iWrite(1);
    b.findAndInsert(Found);
    b.runProgram();
    ok(b, """
Btree
Stuck:  0   size: 2   free: 0   next:  0  leaf: 1
stuckSize: value=2
stuckKeys: value=0, 0=10, 1=20, 2=0, 3=0
stuckData: value=0, 0=1, 1=21, 2=0, 3=0
""");

    b.clearProgram();
    b.stuckKeysField.iWrite(30);
    b.stuckDataField.iWrite(31);
    b.findAndInsert(Found);
    b.runProgram();
    ok(b, """
Btree
Stuck:  0   size: 3   free: 0   next:  0  leaf: 1
stuckSize: value=3
stuckKeys: value=0, 0=10, 1=20, 2=30, 3=0
stuckData: value=0, 0=1, 1=21, 2=31, 3=0
""");

    Layout.Field index = b.btreeIndex();
    b.clearProgram();
    b.allocateLeaf(index);
    b.setRootAsBranch();
    b.runProgram();
    ok(b, """
Btree
Stuck:  0   size: 3   free: 0   next:  0  leaf: 0
stuckSize: value=3
stuckKeys: value=0, 0=10, 1=20, 2=30, 3=0
stuckData: value=0, 0=1, 1=21, 2=31, 3=0
Stuck:  1   size: 0   free: 0   next:  0  leaf: 1
stuckSize: value=0
stuckKeys: value=0, 0=0, 1=0, 2=0, 3=0
stuckData: value=0, 0=0, 1=0, 2=0, 3=0
""");

    b.clearProgram();
    b.stuckKeysField.iWrite(4);
    b.stuckDataField.iWrite(5);
    b.findAndInsert(Found);
    b.runProgram();
    ok(b, """
Btree
Stuck:  0   size: 3   free: 0   next:  0  leaf: 0
stuckSize: value=3
stuckKeys: value=0, 0=10, 1=20, 2=30, 3=0
stuckData: value=0, 0=1, 1=21, 2=31, 3=0
Stuck:  1   size: 1   free: 0   next:  0  leaf: 1
stuckSize: value=1
stuckKeys: value=0, 0=4, 1=0, 2=0, 3=0
stuckData: value=0, 0=5, 1=0, 2=0, 3=0
""");

    b.clearProgram();
    b.stuckKeysField.iWrite(5);
    b.stuckDataField.iWrite(6);
    b.findAndInsert(Found);
    b.runProgram();
    ok(b, """
Btree
Stuck:  0   size: 3   free: 0   next:  0  leaf: 0
stuckSize: value=3
stuckKeys: value=0, 0=10, 1=20, 2=30, 3=0
stuckData: value=0, 0=1, 1=21, 2=31, 3=0
Stuck:  1   size: 2   free: 0   next:  0  leaf: 1
stuckSize: value=2
stuckKeys: value=0, 0=4, 1=5, 2=0, 3=0
stuckData: value=0, 0=5, 1=6, 2=0, 3=0
""");

    b.clearProgram();
    b.stuckKeysField.iWrite(3);
    b.stuckDataField.iWrite(4);
    b.findAndInsert(Found);
    b.runProgram();
    ok(b, """
Btree
Stuck:  0   size: 3   free: 0   next:  0  leaf: 0
stuckSize: value=3
stuckKeys: value=0, 0=10, 1=20, 2=30, 3=0
stuckData: value=0, 0=1, 1=21, 2=31, 3=0
Stuck:  1   size: 3   free: 0   next:  0  leaf: 1
stuckSize: value=3
stuckKeys: value=0, 0=3, 1=4, 2=5, 3=0
stuckData: value=0, 0=4, 1=5, 2=6, 3=0
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

    b.clearProgram(); b.stuckKeysField.iWrite(10); b.stuckDataField.iWrite(11); b.findAndInsert(Found); b.runProgram();
    b.clearProgram(); b.stuckKeysField.iWrite(20); b.stuckDataField.iWrite(21); b.findAndInsert(Found); b.runProgram();
    b.clearProgram(); b.stuckKeysField.iWrite(30); b.stuckDataField.iWrite(31); b.findAndInsert(Found); b.runProgram();
    b.clearProgram(); b.stuckKeysField.iWrite(40); b.stuckDataField.iWrite(41); b.findAndInsert(Found); b.runProgram();
    ok(b, """
Btree
Stuck:  0   size: 4   free: 0   next:  0  leaf: 1
stuckSize: value=4
stuckKeys: value=40, 0=10, 1=20, 2=30, 3=40
stuckData: value=41, 0=11, 1=21, 2=31, 3=41
""");

    b.clearProgram();
    b.splitRootLeaf();
    b.runProgram();
    ok(b, """
Btree
Stuck:  0   size: 1   free: 0   next:  0  leaf: 0
stuckSize: value=1
stuckKeys: value=40, 0=25, 1=25, 2=30, 3=40
stuckData: value=41, 0=1, 1=2, 2=31, 3=41
Stuck:  1   size: 2   free: 0   next:  0  leaf: 1
stuckSize: value=2
stuckKeys: value=0, 0=10, 1=20, 2=0, 3=0
stuckData: value=0, 0=11, 1=21, 2=0, 3=0
Stuck:  2   size: 2   free: 0   next:  0  leaf: 1
stuckSize: value=2
stuckKeys: value=0, 0=30, 1=40, 2=0, 3=0
stuckData: value=0, 0=31, 1=41, 2=0, 3=0
""");
   }

  static void test_splitBranchRoot()
   {final Btree b = test_findAndInsert();

    b.L.P.maxSteps = 500;
//stop(b);
    ok(b, """
Btree
Stuck:  0   size: 3   free: 0   next:  0  leaf: 0
stuckSize: value=3
stuckKeys: value=0, 0=10, 1=20, 2=30, 3=0
stuckData: value=0, 0=1, 1=21, 2=31, 3=0
Stuck:  1   size: 3   free: 0   next:  0  leaf: 1
stuckSize: value=3
stuckKeys: value=0, 0=3, 1=4, 2=5, 3=0
stuckData: value=0, 0=4, 1=5, 2=6, 3=0
""");

    b.clearProgram();
    b.splitRootBranch();
    b.runProgram();
    ok(""+b, """
Btree
Stuck:  0   size: 1   free: 0   next:  0  leaf: 0
stuckSize: value=1
stuckKeys: value=0, 0=20, 1=20, 2=30, 3=0
stuckData: value=0, 0=2, 1=3, 2=31, 3=0
Stuck:  1   size: 3   free: 0   next:  0  leaf: 1
stuckSize: value=3
stuckKeys: value=0, 0=3, 1=4, 2=5, 3=0
stuckData: value=0, 0=4, 1=5, 2=6, 3=0
Stuck:  2   size: 1   free: 0   next:  0  leaf: 0
stuckSize: value=1
stuckKeys: value=0, 0=10, 1=0, 2=0, 3=0
stuckData: value=0, 0=1, 1=0, 2=0, 3=0
Stuck:  3   size: 1   free: 0   next:  0  leaf: 0
stuckSize: value=1
stuckKeys: value=0, 0=30, 1=0, 2=0, 3=0
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
    ok(b, """
Btree
Stuck:  0   size: 2   free: 0   next:  0  leaf: 0
stuckSize: value=2
stuckKeys: value=0, 0=10, 1=20, 2=30, 3=0
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
    ok(b, """
Btree
Stuck:  0   size: 3   free: 0   next:  0  leaf: 0
stuckSize: value=3
stuckKeys: value=30, 0=2, 1=10, 2=20, 3=30
stuckData: value=0, 0=2, 1=1, 2=0, 3=0
Stuck:  1   size: 2   free: 0   next:  0  leaf: 1
stuckSize: value=2
stuckKeys: value=4, 0=3, 1=4, 2=3, 3=4
stuckData: value=4, 0=3, 1=4, 2=3, 3=4
Stuck:  2   size: 2   free: 0   next:  0  leaf: 1
stuckSize: value=2
stuckKeys: value=0, 0=1, 1=2, 2=0, 3=0
stuckData: value=0, 0=1, 1=2, 2=0, 3=0
""");
   }

  static void test_splitLeafAtTop()
   {final Btree b = test_create();
    final Stuck r = b.stuck();
    final Stuck l = b.stuck();
    final Layout.Field Found = b.bit("Found");
    final Layout.Field R     = b.btreeIndex();
    final Layout.Field I     = r.index();
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
    b.saveStuckIntoRoot(r);                     b.setRootAsBranch();
    b.allocateLeaf(L); b.saveStuckInto(l, L);   b.setLeaf(L);
    b.runProgram();
    //stop(b);
    ok(b, """
Btree
Stuck:  0   size: 2   free: 0   next:  0  leaf: 0
stuckSize: value=2
stuckKeys: value=0, 0=10, 1=20, 2=30, 3=0
stuckData: value=0, 0=0, 1=0, 2=1, 3=0
Stuck:  1   size: 4   free: 0   next:  0  leaf: 1
stuckSize: value=4
stuckKeys: value=4, 0=1, 1=2, 2=3, 3=4
stuckData: value=4, 0=1, 1=2, 2=3, 3=4
""");

    b.clearProgram();
    R.iZero();
    I.iZero();
    b.splitLeafAtTop(R);
    b.runProgram();
    //stop(b);
    ok(b, """
Btree
Stuck:  0   size: 3   free: 0   next:  0  leaf: 0
stuckSize: value=3
stuckKeys: value=0, 0=10, 1=20, 2=2, 3=0
stuckData: value=1, 0=0, 1=0, 2=2, 3=1
Stuck:  1   size: 2   free: 0   next:  0  leaf: 1
stuckSize: value=2
stuckKeys: value=4, 0=3, 1=4, 2=3, 3=4
stuckData: value=4, 0=3, 1=4, 2=3, 3=4
Stuck:  2   size: 2   free: 0   next:  0  leaf: 1
stuckSize: value=2
stuckKeys: value=0, 0=1, 1=2, 2=0, 3=0
stuckData: value=0, 0=1, 1=2, 2=0, 3=0
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
   }

  static void newTests()                                                        // Tests being worked on
   {oldTests();
    test_splitLeafAtTop();
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
