//------------------------------------------------------------------------------
// Layout and manipulate a relocatable block of memory
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2025
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Btree in a block on the surface of a silicon chip.

import java.util.*;

class Layout extends Test                                                       // Descriobe and manipulate the memory containing the btree
 {final String                source;                                           // The source string we are going to parse into fields  describing the memory layout
  final Stack<Field>          fields = new Stack<>();                           // Each field parsed from the input string
  final TreeMap<String,Field>  names = new TreeMap<>();                         // Names of each field
  Program                          P = new Program();                           // The code that manipulates the fields

//D1 Layout                                                                     // Describe a memory layout

  Layout() {source = null;}                                                     // An empty layout to create a program other layouts can use

  Layout(String Source)                                                         // A source description of the layout to be parsed into fields
   {source = Source;
    parseFields();
    allocateMemory();                                                           // Allocate memory for all fields
   }

//D2 Fields                                                                     // Describe a field in a memory layout

  class Field                                                                   // The fields in the layout
   {final Layout  layout;                                                       // Containing layout
    final int     line;                                                         // Line at which the layout was parsed
    final int     indent;                                                       // Indentation
    final String  name;                                                         // Name
    final String  cmd;                                                          // Command
    final Integer rep;                                                          // Optional repetition
    final Integer parent;                                                       // Parent
    final Stack<Field>dimensions = new Stack<>();                               // Dimensions of field
    final Stack<Field>children   = new Stack<>();                               // Children of an item
    final boolean spacer, array, bit, var;                                      // Classification - a spacer is a bit or a var as they actually take up space - or a character in "The Caves of Steel"
    BitSet[]memory;                                                             // Memory for this field
    int     value;                                                              // The last value read from the memory of this field

    Field(int line, int indent, String name, String cmd,
          Integer rep, Integer parent)
     {this.layout = Layout.this;
      this.line   = line;
      this.indent = indent;
      this.name   = name;
      this.cmd    = cmd;
      this.rep    = rep;
      this.parent = parent;
      array       = cmd.equals("array");
      bit         = cmd.equals("bit");
      var         = cmd.equals("var");
      spacer      = bit || var;
      fields.push(this);
      names.put(name, this);                                                    // Already checked that the name is non unique when we had access to the location of this field in the source
     }

    boolean hasParent() {return getParent() != null;}                           // Whether a field has a parent.  Variables that do not are top most variables
    boolean asBoolean() {return value > 0;}                                     // Field value as a boolean

    Field getParent()                                                           // Parent of a field
     {if (parent == null) return null;
      return fields.elementAt(parent);
     }

    String dump()                                                               // Dump the details of a field
     {final StringBuilder s = new StringBuilder();
      s.append("Field(line="+line);
      s.append(", indent="  +indent);
      s.append(", name="    +name);
      s.append(", cmd="     +cmd);
      if (rep    != null) s.append(", rep="   +rep);
      if (parent != null) s.append(", parent="+getParent().name);
      return ""+s+")";
     }

    public String toString()                                                    // Dump the memory associated with a field
     {final StringBuilder s = new StringBuilder();
      s.append(name+": value="   +value);
      if (memory != null)
       {final int d = memory.length;
        if (d > 0)
         {for (int i = 0; i < d; i++)
           {s.append(", "+i+"="+getIntFromBits(memory[i]));
           }
         }
       }
      return ""+s;
     }

    Integer rep()                                                               // The width of the element in bits or the array dimension
     {if (bit) return 1;
      return rep;
     }

    int dims() {return dimensions.size();}                                      // The number if containing arrays - if there are none the field has no backing memory only its cirrent value

    int dimProduct()                                                            // The product of the dimensions
     {if (dims() == 0) return 1;                                                // No dimensions
      if (dims() == 1) return dimensions.firstElement().rep;                    // One dimension
      int n = 1;
      for(Field d : dimensions) n *= d.rep;                                     // Multiple dimensions
      return n;
     }

    String dimensions()                                                         // The dimensions as a string
     {final Stack<String> s = new Stack<>();
      if (dims() == 0) return "";                                               // No dimensions
      if (dims() == 1) return ""+dimensions.firstElement().rep;                 // One dimension
      for(Field d : dimensions) s.push(""+d.rep);                               // Several dimensions
      int n = 1;
      for(Field d : dimensions) n *= d.rep;
      return joinStrings(s, "*")+" = "+n;
     }

    String children()                                                           // The names of the children as a string
     {final Stack<String> s = new Stack<>();
      for(Field c : children) s.push(""+c.name);
      return joinStrings(s, ", ");
     }

    void allocateMemory()                                                       // Allocate memory for this field if is a var or bit and part of an array. Otherwise it is just temporary
     {if (rep() == null || dims() == 0) return;                                 // Only vars and bits are allocated memeory and even then only if they are part of an array
      final int N = dimProduct();
      memory = new BitSet[N];                                                   // Array of bit sets.  This is inefficient for representing bit fields in Java but not a problem in Verilog and, ultimately, it is the Verilog that counts.
      for (int i = 0; i < N; i++) memory[i] = new BitSet(rep());                // Memory at each index
     }

    Field checkVar()                                                            // Check that this is a bit or var field - a bit is a var containing just one bit
     {if (bit || var) return this;
      stop("Expected a bit or a var but got a:", cmd, "called", name);
      return null;
     }

    int getIntFromBits(BitSet b)                                                // Get the value of a bitset as an integer to the extent that the integer can accept
     {return b.length() == 0 ? 0 : (int) b.toLongArray()[0];                    // Relying on the fact that the Java is only ever test code unlike the Verilog.
     }

    void setBitsFromInt(BitSet b, int value)                                    // Set a bit set to as much of an integer as it can accept
     {final int l = min(rep(), Integer.SIZE-1);
      b.clear();                                                                // Zero the memory
      for (int i = 0; i < l; i++)                                               // Set each bit in the bitset if the corresponding bit in the value is set
       {if (((value >> i) & 1) != 0)
         {b.set(i);
         }
       }
     }

    int convolute(Field...j)                                                    // Convolute the dimensions of this field with the supplied top level vars acting as array indices to locat the index of an element in an array
     {int i = j[0].value;                                                       // Current value of var
      final int J = j.length;
      for (int c = 1; c < J; c++)                                               // Each dimension beyond the first one contributes to the indexs.  The first dimension determines the size but not the location of an element in the array
       {final int    d = dimensions.elementAt(c).rep();
        final Field  f = j[c];
        final int    v = f.value;
        final String m = "Index: "+v+"from: "+f.name+" is";
        if (v <  0) stop(m, "negative");                                        // Index out of range low
        if (v >= d) stop(m, "is greater than or equal to:", d);                 // Index out of range high
        i = i * d + v;                                                          // Move up one dimension
       }
      return i;
     }

    String convoluteV(Field...j)                                                // Convolute the dimensions of this field with the supplied top level vars acting as array indices to locat the index of an element in an array
     {String s = ""+j[0].value;                                                 // Current value of var
      final int J = j.length;
      for (int c = 1; c < J; c++)                                               // Each dimension beyond the first one contributes to the indexs.  The first dimension determines the size but not the location of an element in the array
       {final int    d = dimensions.elementAt(c).rep();
        final Field  f = j[c];
        final int    v = f.value;
        s = "("+s+") * "+d+" + "+v;                                             // Move up one dimension
       }
      return s;
     }

//D3 Read                                                                       // Read values from memory

    void read(int index)                                                        // Create an instruction that loads the value of this field from the constant indexed element of the memory associated with this field
     {value = getIntFromBits(memory[index]);
     }

    String readV(String index)                                                  // Create an instruction that loads the value of this field from the constant indexed element of the memory associated with this field
     {return name+"_value = "+name+"_memory["+index+"];";
     }

    void iRead(int index)                                                       // Create an instruction that loads the value of this field from the constant indexed element of the memory associated with this field
     {final Field f = checkVar();
      P.new Instruction()
       {void   action () {f.read(index);}
        String verilog() {return f.readV(""+index);}
       };
     }

    void read(Field...Indices)     {read(convolute(Indices));}                  // Create an instruction that loads the value of this field from the variably indexed element of the memory associated with this field
    String readV(Field...Indices)  {return readV(convoluteV(Indices));}         // Create an instruction that loads the value of this field from the variably indexed element of the memory associated with this field

    void iRead(Field...Indices)                                                 // Create an instruction that loads the value of this field from the variably indexed element of the memory associated with this field
     {final Field f = checkVar();
      P.new Instruction()
       {void   action () {f.read(Indices);}
        String verilog() {return f.readV(Indices);}
       };
     }

    void readNext(Field...Indices)                                              // Create an instruction that loads the value of this field from the one plus variably indexed element of the memory associated with this field
     {final int index = convolute(Indices);
      value = getIntFromBits(memory[index+1]);
     }

    String readNextV(Field...Indices)                                           // Create an instruction that loads the value of this field from the one plus variably indexed element of the memory associated with this field
     {final String index = convoluteV(Indices);
      return name+"_value = "+name+"_memory["+index+"+1];";
     }

    void iReadNext(Field...Indices)                                             // Create an instruction that loads the value of this field from the one plus variably indexed element of the memory associated with this field
     {final Field f = checkVar();
      P.new Instruction()
       {void   action () {readNext(Indices);}
        String verilog() {return readNextV(Indices);}
       };
     }

//D3 Write                                                                      // Write values into memory

    void write(int Value)
     {final Field f = this;
      if (logTwo(Value) > f.rep())
       {P.stopProgram("Value too big to be written into array");
        return;
       }
      final BitSet b = new BitSet(f.rep());                                     // Only used locally - does not become part of memory
      f.setBitsFromInt(b, Value);
      f.value = f.getIntFromBits(b);                                            // So the value matches what would actually be written into memory
     }

    String writeV(int Value)
     {final Field f = this;
      return f.name+"_value = "+Value+";";
     }

    void iWrite(int Value)                                                      // Create an instruction that sets the value of this field but does not modify the memory backing the field
     {final Field  f = checkVar();
      P.new Instruction()
       {void   action () {f.write(Value);}
        String verilog() {return f.writeV(Value);}
       };
     }

    void write(int Value, int Index)                                            // Create an instruction that sets the value of this field and updates the constant indexed element of the memory associated with this field with the same value
     {final Field f = this;
      if (logTwo(value) > f.rep())
       {P.stopProgram("Value too big to be written into array");
        return;
       }
      final BitSet b = f.memory[Index];                                         // Bit set in memory holding value at this index
      f.setBitsFromInt(b, Value);
      f.value = f.getIntFromBits(b);                                            // So the value matches what is actually in memory
     }

    String writeV(int Value, int Index)                                            // Create an instruction that sets the value of this field and updates the constant indexed element of the memory associated with this field with the same value
     {return name+"_memory["+Index+"] = "+Value+";";
     }

    void iWrite(int Value, int Index)                                           // Create an instruction that sets the value of this field and updates the constant indexed element of the memory associated with this field with the same value
     {final Field f = checkVar();
      P.new Instruction()
       {void   action () {f.write(Value, Index);}
        String verilog() {return f.writeV(Value, Index);}
       };
     }

    void write(Field...Indices)                                                 // Create an instruction that sets the value of this field and updates the variable indexed element of the memory associated with this field with the same value
     {final Field f = this;
      if (logTwo(value) > f.rep())
       {P.stopProgram("Value too big to be wrtitten into array");
        return;
       }
      final int index = convolute(Indices);
      final BitSet b  = f.memory[index];                                        // Bit set in memory holding value at this index
      f.setBitsFromInt(b, value);
      f.value = f.getIntFromBits(b);                                            // So the value matches what is actually in memory
     }

    String writeV(Field...Indices)                                              // Create an instruction that sets the value of this field and updates the variable indexed element of the memory associated with this field with the same value
     {final Field f = this;
      final String index = convoluteV(Indices);
      return f.name+"_value = "+f.name+"_memory["+index+"];";
     }

    void iWrite(Field...Indices)                                                // Create an instruction that sets the value of this field and updates the variable indexed element of the memory associated with this field with the same value
     {final Field f = checkVar();
      P.new Instruction()
       {void   action () {f.write(Indices);}
        String verilog() {return f.writeV(Indices);}
       };
     }

    void constant(int Value, Field...Indices)                                   // Create an instruction to set an array element to a constant
     {final Field f = this;
      if (Indices.length == 0)                                                  // No indices
       {f.value = Value;
        return;
       }
      if (logTwo(Value) > f.rep())
       {P.stopProgram("Value too big to be written into array");
        return;
       }
      final int index = f.convolute(Indices);                                   // Convolute the indices
      final BitSet b  = f.memory[index];                                        // Bit set in memory holding value at this index
      f.setBitsFromInt(b, Value);
      f.value = f.getIntFromBits(b);                                            // So the value matches what is actually in memory
     }

    String constantV(int Value, Field...Indices)                                // Create an instruction to set an array element to a constant
     {final Field f = this;
      final String index = convoluteV(Indices);
      return f.name+"_memory["+index+"] = "+value+";";
     }

    void iConstant(int Value, Field...Indices)                                  // Create an instruction to set an array element to a constant
     {final Field f = checkVar();
      P.new Instruction()
       {void   action () {       f.constant(Value, Indices);}
        String verilog() {return f.constantV(Value, Indices);}
       };
     }

    void one     (Field...indices) {        constant(1, indices);}              // Create an instruction to set a field to one
    void zero    (Field...indices) {        constant(0, indices);}              // Create an instruction to set a field to zero
    String vOne  (Field...indices) {return constantV(1, indices);}              // Create an instruction to set a field to one
    String vZero (Field...indices) {return constantV(0, indices);}              // Create an instruction to set a field to zero

    void iOne (Field...indices) {iConstant(1, indices);}                        // Create an instruction to set a field to one
    void iZero(Field...indices) {iConstant(0, indices);}                        // Create an instruction to set a field to zero

//D3 Move                                                                       // Instructions that copy data from one memory location to another

    void    move(Field Source) {value = Source.value;}                          // Copy the source value to the target. To write into backing memory as well call iWrite() as well
    String moveV(Field Source) {return name+"_value = "+Source.name+"_value;";}

    void iMove(Field Source) {iAdd(Source);}                                    // Copy the source value to the target. To write into backing memory as well call iWrite() as well

    void move(int TargetIndex, Field Source)                                    // Copy the indexed source memory into the indexed target memory
     {setBitsFromInt(memory[TargetIndex], Source.value);
     }

    void iMove(int TargetIndex, Field Source)                                   // Copy the indexed source memory into the indexed target memory
     {P.new Instruction()
       {void action() {move(TargetIndex, Source);}
       };
     }

    void move (int TargetIndex, Field Source, int SourceIndex)                  // Copy the indexed source memory into the indexed target memory
     {memory[TargetIndex] = (BitSet)Source.memory[SourceIndex].clone();
     }

    String moveV(int TargetIndex, Field Source, int SourceIndex)                // Copy the indexed source memory into the indexed target memory
     {return name+"_memory["+TargetIndex+"] = "+Source.name + "_memory["+SourceIndex+"];";
     }

    void iMove(int TargetIndex, Field Source, int SourceIndex)                  // Copy the indexed source memory into the indexed target memory
     {P.new Instruction()
       {void   action () {        move(TargetIndex, Source, SourceIndex);}
        String verilog() {return moveV(TargetIndex, Source, SourceIndex);}
       };
     }

//D3 Arithmetic                                                                 // Instructions that do arithmetic

    void    dec() {value--;}                                                    // Decrement the value of this field
    String decV() {return name+"_value = "+name+"_value - 1;";}                 // Decrement the value of this field

    void iDec()                                                                 // Decrement the value of this field
     {final Field f = checkVar();
      P.new Instruction()
       {void   action () {       f. dec();}
        String verilog() {return f.decV();}
       };
     }

    void   inc () {value++;}                                                    // Increment the value of this field
    String incV() {return name+"_value = "+name+"_value + 1;";}                 // Decrement the value of this field

    void iInc()                                                                 // Increment the value of this field
     {final Field f = checkVar();
      P.new Instruction()
       {void action()    {       f.inc();}
        String verilog() {return f.incV();}
       };
     }

    void iAdd(Field...Source)                                                   // Add the values of the source fields and store in the target. If no source fields are supplied trhe source is zeroed.  IF one field is supplied the source value is copied in to the target.  Otherwise the source fields are summed and the result stored in the target value
     {final Field t = checkVar();
      switch(Source.length)
       {case 0: P.new Instruction()
         {void   action () {t.value = 0;}
          String verilog() {return t.name+"_value = 0;";}
         };
        break;
        case 1: P.new Instruction()
         {void   action () {t.value = Source[0].value;}
          String verilog() {return t.name+"_value = "+Source[0].name+"_value;";}
         };
        break;
        case 2: P.new Instruction()
         {void   action () {t.value = Source[0].value + Source[1].value;}
          String verilog() {return t.name+"_value = "+Source[0].name+"_value"+" + "+Source[1].name+"_value;";}
         };
        break;
        default: P.new Instruction()
         {void action()
           {t.value = 0; for(Field s : Source) t.value += s.value;
           }
         };
        break;
       };
     }

    void   half()  {value >>>= 1;}                                              // Divide the value of a field by two
    String halfV() {return name+"_value = "+name+"_value >>> + 1;";}            // Divide the value of a field by two

    void iHalf()                                                                // Divide the value of a field by two
     {P.new Instruction()
       {void   action () {       half();}
        String verilog() {return halfV();}
       };
     }
   }

//D2 Programs                                                                   // Define a program to manipulate the layout

  class Program                                                                 // Program definition
   {final Stack<Instruction>      code = new Stack<>();                         // The code that manipulates the fields
    final Stack<Label>          labels = new Stack<>();                         // Labels into the code
    int                       maxSteps = 200;                                   // Maximum number of steps to execute
    int                             pc = 0;                                     // The index of the next instruction to be executed
    Integer                     nextPc = null;                                  // The next program counter requested
    String                          rc = null;                                  // The result of executing the program.  If null then no problems were detected
    boolean   supressErrorMessagePrint = false;                                 // Do not print error message from iStop() during testing if true

//D3 Conditional Programming                                                    // Conditional changes to the flow of execution of a program modifying the memory layout

    class Label                                                                 // Labels label instructions in the code
     {final int number;                                                         // Label number
      int offset;                                                               // Offset in code of this label
      Label()    {set(); number = P.labels.size(); P.labels.push(this);}        // Initially at the current end of the code
      void set() {offset = P.code.size();}                                      // Track all labels created
     }

    void Goto     (Label label)                                                 // Goto a label unconditionally
     {nextPc = label.offset;
     }
    void GoNotZero(Label label, Field condition)                                // Go to a specified label if the value of a field is not zero
     {if (condition.asBoolean()) nextPc = label.offset;
     }
    void GoZero   (Label label, Field condition)                                // Go to a specified label if the value of a field is zero
     {if (!condition.asBoolean()) nextPc = label.offset;
     }

    void iGoto(Label label)                                                     // Goto a label unconditionally
     {P.new Instruction()
       {void   action () {Goto(label);}
        String verilog() {return "program_pc = program_label["+label.number+"];";}
       };
     }

    void iGoNotZero(Label label, Field condition)                               // Go to a specified label if the value of a field is not zero
     {P.new Instruction()
       {void action() {GoNotZero(label, condition);}
        String verilog() {return "if (condition) program_pc = program_label["+label.number+"];";}
       };
     }

    void iGoZero(Label label, Field condition)                                  // Go to a specified label if the value of a field is zero
     {P.new Instruction()
       {void action() {GoZero(label, condition);}
        String verilog() {return "if (!condition) program_pc = program_label["+label.number+"];";}
       };
     }

    abstract class If                                                           // An if statement
     {final Label Else = new Label(), End = new Label();                        // Components of an if statement

      If (Field Condition)                                                      // If a condition
       {iGoZero(Else, Condition);                                               // Branch on the current value of condition
        Then();
        iGoto(End);
        Else.set();
        Else();
        End.set();
       }
      void Then() {}
      void Else() {}
     }

    abstract class Block                                                        // A block that can be continued or exited
     {final Label start = new Label(), end = new Label();                       // Labels at start and end of block to facilitate continuing or exiting
      Block()
       {code();
        end.set();
       }
      abstract void code();
     }

    abstract class For                                                          // A for loop
     {final Label start     = new Label(), end = new Label();                   // Labels at start and end of block to facilitate continuing or exiting
      final Field loop      = variable("loop",      Integer.SIZE);
      final Field condition = variable("condition", 1);

      For(Layout.Field count)
       {P.new Instruction()
         {void action()
           {loop.value = 0;
           }
         };

        start.set();
        P.new Instruction()
         {void action()
           {condition.value = loop.value < count.value ? 1 : 0;
           }
         };

        iGoZero(end, condition);
        code();
        loop.iInc();
        iGoto(start);
        end.set();
       }
      void code() {};
     }

//D3 Execute                                                                    // Execute instructions in a program to modify the memory described by the layout

    abstract class Instruction                                                  // Instructions used to manipulate the fields
     {final String traceBack = traceBack();                                     // Line at which this instruction was created

      Instruction() {P.code.push(this);}                                        // Add the instruction to the code

      abstract void action();                                                   // Override this method to specify what the instruction does using Java code
      String verilog() {return " /* verilog code needed */";}                   // Override this method to specify what the instruction does using Verilog code
     }

    void clearProgram()                                                         // Clear the code associated with a program so we can create a new program
     {code.clear(); labels.clear();
      P.rc = null;
      supressErrorMessagePrint = false;
     }

    void runProgram()                                                           // Run the program code
     {rc = null;                                                                // Clear the return code
      int  i = 0;
      final int size = code.size();                                             // Programs must not add instrructions to the code
      for (i = pc = 0; pc < code.size() && i < maxSteps; ++i)
       {nextPc = null;
        code.elementAt(pc).action();
        if (code.size() != size)
         {stopProgram("Additional instructions being defined inside an instruction at instruction: "+pc);
          return;
         }
        if (nextPc != null) pc = nextPc; else pc++;                             // Interpret next program counter
       }
      if (pc < code.size()) stop("Out of steps after :", i);
     }

    void stopProgram(final String message)                                      // Halt program execution with a message
     {rc = message;                                                             // Use the message as a result code
      if (!supressErrorMessagePrint)
       {say(message, code.elementAt(pc).traceBack);                            // Write the supplied message
       }
      pc = code.size();                                                         // Halt the program
     }

    void iStop(final String message)                                            // Halt program execution with a message
     {P.new Instruction()
       {void action() {stopProgram(message);}
       };
     }

//D2 Verilog                                                                    // Generate Verilog

    protected void generateVerilog()                                            // Generate verilog
     {final int N = code.size();
      for (int i = 0; i < N; i++)
       {say(code.elementAt(i).verilog());
       }
     }
   }

  void clearProgram() {P.clearProgram();}                                       // Clear the program code
  void runProgram()   {P.runProgram();}                                         // Run the program code
  void stopProgram(final String message) {P.stopProgram(message);}              // Halt program execution with a message
  void continueProgram(Program p) {P = p;}                                      // Continue with an existing program

  Program startNewProgram()                                                     // Start a new pogram returning the previous program so that it can be continued at some point
   {final Program p = P;
    P = new Program();
    return p;
   }

//D2 Parsing                                                                    // Parse the source description of a memory layout

  Field locateFieldByName(String name) {return names.get(name);}                // Locate a field by name
  Field onlyField()                                                             // Retrieve the only field in a layout
  {if (names.size() != 1) stop("One name only must be defined");
   return names.firstEntry().getValue();
  }

  void allocateMemory()                                                         // Allocate memory for all fields that actually use memory
   {for(Field f: fields) if (f.spacer && f.dims() > 0) f.allocateMemory();
   }

  Integer locatePreviousElement(int indent, String location)                    // The index of the previous field ignoring the dependencies of the previous field
   {if (indent >= fields.lastElement().indent + 2) return fields.size()-1;      // Deeper indentation acceptable so the previous field is the last one parsed
    if (indent >  fields.lastElement().indent)
      stop("Minimum indentation of 2 spaces required", location);
    for(int i = fields.size(); i > 0; --i)                                      // Each prior layout
     {final int in = fields.elementAt(i-1).indent;                              // Indentation of prior layout
      if (in == indent) return i-1;                                             // Matched indentation of a prior layout
     }
    stop("Indentation does not match that of any previous line", location);     // No matching previous layout
    return null;
   }

  private void parseFields()                                                    // Parse the layout description into fields
   {final String[]lines = source.split("\n");                                   // Split source into lines
    for(int l = 0; l < lines.length; ++l)                                       // Lines
     {final String line   = lines[l];                                           // Each line
      if (line.matches("\\A\\s*#")) continue;                                   // Comment
      final String E      = "on line: "+(l + 1)+"\n"+line;                      // Error line, 1 based
      final int indent    = line.length() - line.stripLeading().length();       // Indentation depth
      final String[]words = line.trim().split("\\s+");                          // Words in line
      if (words.length < 2) stop("Not enough operands", E);                     // Need at least a name and a type
      final String name   = words[0];                                           // First word is the name
      final String cmd    = words[1].toLowerCase();                             // Command is the second word
      if (!cmd.matches("array|bit|var"))                                        // Check command
        stop("Expected one of: array, bit or var", E);
      final boolean bit   = cmd.equals("bit");                                  // Need at least a name and a type
      final int eo = bit ? 2 : 3, ao = words.length;                            // Number of operands expected and actually found
      if (ao != eo) stop("Not enough operands", E, "expected:", eo, "found:", ao);
      final String Rep = ao > 2 ? words[2] : null;
      if (Rep != null && !Rep.matches("\\A\\d+\\Z"))                            // Repetition if present must be numeric
        stop("Repetition:", Rep, "should be an integer", E);
      final int rep = Rep == null ? 1 : Integer.parseInt(Rep);                  // Repetition or width

      if (names.containsKey(name)) stop("Duplicate name:", name, E);            // Require names to be unique

      if (fields.size() == 0)                                                   // First line parsed
       {new Field(l, indent, name, cmd, rep, null);                             // Details of the first line parsed
        continue;
       }

      final Integer prev = locatePreviousElement(indent, E);                    // Previous field

      final Field p = fields.elementAt(prev);                                   // Previous field
      if (indent > p.indent)                                                    // Indenting further
       {final Field f = new Field(l, indent, name, cmd, rep, prev);             // Details of the fields of this line
        p.children.push(f);                                                     // The children associated with each parent
        continue;
       }

      final Field F = new Field(l, indent, name, cmd, rep, p.parent);           // Indenting at the same level as a previous field
      if (p.hasParent()) p.getParent().children.push(F);                        // The children associated with each parent. I f a field hass no parent then it is a top field and can be used for indexing fields under arrays
     }

    for(Field p : fields)                                                       // Dimensions of each spacer
     {if (!p.spacer) continue;                                                  // Items which have dimensions
      final Stack<Field>d = p.dimensions;
      for(Field q = p.getParent(); q != null; q = q.getParent())                // Up through parents
       {if (!q.array) continue;                                                 // Find arrays
        d.insertElementAt(q, 0);                                                // Unshift containing arrays
       }
     }

    for(Field p : fields)                                                       // Check children counts
     {final int    line = p.line + 1;
      final String name = p.name;
      final String cmd  = p.cmd;
      final int    k    = p.children.size();
      final String loc = name+" from line: "+line;
      if (p.bit    && k >  0) stop("Bit:",    loc, "cannot have child layouts");
      if (p.var    && k >  0) stop("Var:",    loc, "cannot have child layouts");
      if (p.array  && k <  1) stop("Array:",  loc, "must have exactly one or more child layouts");
     }
   }

  Layout.Field variable(String name, int size)                                  // Create a variable
   {final Layout l = new Layout(String.format("""
%s var %d
""", name, size));

    l.P = P;
    return l.onlyField();
   }

//D2 Printing                                                                   // Print the results if parsing a memory layout

  public String toString()                                                      // Print the fields of the input lines
   {final Stack<StringBuilder> S = new Stack<>();                               // Print of fields
    final String t = String.format
       ("%4s  %11s  %-32s  %-8s  %-8s  %8s  %8s  %-32s  %-32s",
"#",
"Indent",
"Name",
"Value___",
"Command",
"Rep",
"Parent",
"Children",
"Dimension");
    S.push(new StringBuilder(t));

    for (int i = 0; i < fields.size(); i++)
     {final Field  f = fields.elementAt(i);
      final String c = f.cmd;
      final String C = f.children();
      final String D = f.dimensions();
      final int    d = f.indent;
      final String n = " ".repeat(d)+f.name;
      final String p = f.parent != null ? f.getParent().name : "";
      final String r = f.rep    != null ? String.format("%8d", f.rep)              : "";
      final String v = String.format("%8d", f.value);
      final String s = String.format
       ("%4d  %11d  %-32s  %-8s  %-8s  %8s  %8s  %-32s  %-32s",
        i,    d,    n,    v, c,     r,   p,   C,     D);
      S.push(new StringBuilder(s));
     }
    squeezeVerticalSpaces(S);
    return joinStringBuilders(S, "\n")+"\n";
   }

//D2 Tests                                                                      // Test memory layouts

  protected static void test_parse()
   {Layout l = new Layout("""
A array 2
  a var 4
  b bit
  B array 4
    a1 bit
    b1 var 2
    C array 2
      a2 bit
      b2 var 5
""");

    //stop(l);
    ok(l, """
  #  Indent  Name      Value___  Command  Rep  Parent  Children   Dimension
  0       0  A                0  array      2          a, b, B
  1       2    a              0  var        4       A             2
  2       2    b              0  bit        1       A             2
  3       2    B              0  array      4       A  a1, b1, C
  4       4      a1           0  bit        1       B             2*4 = 8
  5       4      b1           0  var        2       B             2*4 = 8
  6       4      C            0  array      2       B  a2, b2
  7       6        a2         0  bit        1       C             2*4*2 = 16
  8       6        b2         0  var        5       C             2*4*2 = 16
""");

    Field b1 = l.locateFieldByName("b1");
    ok(b1.dimProduct(), 8);
    l.allocateMemory();
   }

  protected static void test_parse_top()
   {Layout l = new Layout("""
a var 4
b bit
A array 1
  a1 bit
  b1 var 5
c var 2
""");

    //stop(l);
    ok(l, """
  #  Indent  Name  Value___  Command  Rep  Parent  Children  Dimension
  0       0  a            0  var        4
  1       0  b            0  bit        1
  2       0  A            0  array      1          a1, b1
  3       2    a1         0  bit        1       A            1
  4       2    b1         0  var        5       A            1
  5       0  c            0  var        2
""");

    Field a = l.locateFieldByName("a");
    Field b = l.locateFieldByName("b");
    Field c = l.locateFieldByName("c");
    ok(a.rep(), 4);
    ok(b.rep(), 1);
    ok(c.rep(), 2);
   }

  protected static void test_vars()
   {Layout l = new Layout("""
i var 4
A array 2
  a var 4
v var 4
""");

    Field i = l.locateFieldByName("i");
    Field a = l.locateFieldByName("a");
    Field v = l.locateFieldByName("v");

    i.iWrite(0); v.iWrite(2); a.iWrite(i);
    i.iWrite(1); v.iWrite(4); a.iWrite(i);

    l.runProgram();

    //stop(l);
    ok(l, """
  #  Indent  Name  Value___  Command  Rep  Parent  Children  Dimension
  0       0  i            1  var        4
  1       0  A            0  array      2          a
  2       2    a          0  var        4       A            2
  3       0  v            4  var        4
""");

    l.clearProgram(); i.iWrite(0); a.iRead(i); l.runProgram();
    //stop(l);
    ok(l, """
  #  Indent  Name  Value___  Command  Rep  Parent  Children  Dimension
  0       0  i            0  var        4
  1       0  A            0  array      2          a
  2       2    a          0  var        4       A            2
  3       0  v            4  var        4
""");

    l.clearProgram();  i.iWrite(1); a.iRead(i); l.runProgram();
    //stop(l);
    ok(l, """
  #  Indent  Name  Value___  Command  Rep  Parent  Children  Dimension
  0       0  i            1  var        4
  1       0  A            0  array      2          a
  2       2    a          0  var        4       A            2
  3       0  v            4  var        4
""");
   }

  protected static void test_array()
   {Layout l = new Layout("""
i var 4
j var 4
A array 2
  B array 3
    b var 4
v var 4
""");

    Field i = l.locateFieldByName("i");
    Field j = l.locateFieldByName("j");
    Field A = l.locateFieldByName("A");
    Field a = l.locateFieldByName("a");
    Field B = l.locateFieldByName("B");
    Field b = l.locateFieldByName("b");
    Field v = l.locateFieldByName("v");

    l.clearProgram();
    for   (int x = 0; x < A.rep; x++)
     {for (int y = 0; y < B.rep; y++)
       {i.iWrite(x); j.iWrite(y); b.iWrite(2 * x + y); b.iWrite(i, j);
       }
     }
    l.runProgram();

    for   (int y = 0; y < B.rep; y++)
     {for (int x = 0; x < A.rep; x++)
       {l.clearProgram();
        i.iWrite(x); j.iWrite(y); b.iRead(i, j);
        l.runProgram();
        ok(b.value, 2 * x + y);
       }
     }
    ok(b, "b: value=4, 0=0, 1=1, 2=2, 3=2, 4=3, 5=4");

    l.clearProgram(); i.iWrite(1); j.iWrite(1); b.iZero(i, j); l.runProgram();
    ok(b, "b: value=0, 0=0, 1=1, 2=2, 3=2, 4=0, 5=4");

    l.clearProgram(); i.iWrite(1); j.iWrite(1); b.iOne (i, j); l.runProgram();
    ok(b, "b: value=1, 0=0, 1=1, 2=2, 3=2, 4=1, 5=4");

    l.clearProgram(); i.iWrite(0); j.iWrite(0); b.iReadNext(i, j); l.runProgram(); ok(b.value, 1);
    l.clearProgram(); i.iWrite(0); j.iWrite(1); b.iReadNext(i, j); l.runProgram(); ok(b.value, 2);
    l.clearProgram(); i.iWrite(1); j.iWrite(0); b.iReadNext(i, j); l.runProgram(); ok(b.value, 1);
    l.clearProgram(); i.iWrite(1); j.iWrite(1); b.iReadNext(i, j); l.runProgram(); ok(b.value, 4);
   }

  protected static void test_add()
   {Layout l = new Layout("""
a var 4
b var 4
c var 4
d var 4
e var 4
f var 4
""");

    Field a = l.locateFieldByName("a");
    Field b = l.locateFieldByName("b");
    Field c = l.locateFieldByName("c");
    Field d = l.locateFieldByName("d");
    Field e = l.locateFieldByName("e");
    Field f = l.locateFieldByName("f");

    a.iWrite(2); a.iDec();
    b.iWrite(1); b.iInc();
    c.iAdd();
    d.iAdd(b);
    e.iAdd(a, b);
    f.iAdd(a, b, c, d, e);
    l.runProgram();
    //stop(l);
    ok(l, """
  #  Indent  Name  Value___  Command  Rep  Parent  Children  Dimension
  0       0  a            1  var        4
  1       0  b            2  var        4
  2       0  c            0  var        4
  3       0  d            2  var        4
  4       0  e            3  var        4
  5       0  f            8  var        4
""");
   }

  protected static void test_half()
   {Layout l = new Layout("""
a var 4
""");

    Field a = l.locateFieldByName("a");
    a.iWrite(3);
    a.iHalf();
    l.runProgram();
    //stop(a);
    ok(a, "a: value=1");
   }

  protected static void test_if()
   {Layout l = new Layout("""
a var 4
b var 4
t var 4
e var 4
""");

    Field a = l.locateFieldByName("a");
    Field b = l.locateFieldByName("b");
    Field t = l.locateFieldByName("t");
    Field e = l.locateFieldByName("e");

    a.iWrite(1);
    l.P.new If(a)
     {void Then() {t.iWrite(2);}
      void Else() {e.iWrite(3);}
     };
    l.P.new If(b)
     {void Then() {t.iWrite(4);}
      void Else() {e.iWrite(5);}
     };
    l.runProgram();

    //stop(l);
    ok(l, """
  #  Indent  Name  Value___  Command  Rep  Parent  Children  Dimension
  0       0  a            1  var        4
  1       0  b            0  var        4
  2       0  t            2  var        4
  3       0  e            5  var        4
""");
   }

  protected static void test_block()
   {Layout l = new Layout("""
a var 4
b var 4
c var 4
d var 4
""");

    Field a = l.locateFieldByName("a");
    Field b = l.locateFieldByName("b");
    Field c = l.locateFieldByName("c");
    Field d = l.locateFieldByName("d");

    a.iWrite(1);
    l.P.new Block()
     {void code()
       {c.iWrite(1);
        l.P.iGoNotZero(end, a);
        d.iWrite(2);
       };
     };
    l.runProgram();

    //stop(l);
    ok(l, """
  #  Indent  Name  Value___  Command  Rep  Parent  Children  Dimension
  0       0  a            1  var        4
  1       0  b            0  var        4
  2       0  c            1  var        4
  3       0  d            0  var        4
""");
   }

  protected static void test_for()
   {Layout       l = new Layout();
    Layout.Field i = l.variable("index", 4);
    Layout.Field j = l.variable("loop", 4);

    for (int J = 0; J < 8; ++J)
     {l.clearProgram();
      i.iWrite(0);
      j.iWrite(J);
      l.P.new For(j)
       {void code()
         {i.iInc();
         }
       };
      l.runProgram();
      ok(i, "index: value="+J);
     }
   }

  protected static void test_stop()
   {Layout l = new Layout("""
a var 4
""");

    Field a = l.onlyField();
    l.P.supressErrorMessagePrint = true;
    l.P.iStop("Stopped");
    a.iWrite(1);
    l.runProgram();

    //stop(l);
    ok(l, """
  #  Indent  Name  Value___  Command  Rep  Parent  Children  Dimension
  0       0  a            0  var        4
""");

    ok(l.P.rc.equals("Stopped"));
   }

  protected static void test_variable()
   {final Layout l = new Layout();
    Layout.Field a = l.variable("a", 4);
    a.iWrite(2);
    l.P.runProgram();

    //stop(a.layout);
    ok(a.layout, """
  #  Indent  Name  Value___  Command  Rep  Parent  Children  Dimension
  0       0  a            2  var        4
""");

    //stop(a);
    ok(a, "a: value=2");
    l.P.clearProgram();
    a.iZero();
    l.P.runProgram();
    ok(a, "a: value=0");
   }

  protected static void test_stackProgram()
   {final Layout l = new Layout();
    Layout.Field a = l.variable("a", 4);

    a.iWrite(1);
    Program p = l.startNewProgram();
    a.iWrite(2);
    l.P.runProgram();
    ok(a, "a: value=2");
    l.continueProgram(p);
    l.P.runProgram();
    ok(a, "a: value=1");
   }

  protected static void test_move()
   {Layout l = new Layout("""
A array 4
    a var 4
B array 4
    b var 4
""");

    Field A = l.locateFieldByName("A");
    Field a = l.locateFieldByName("a");
    Field b = l.locateFieldByName("b");

    l.clearProgram();
    for   (int i = 0; i < A.rep; i++) a.iWrite(i, i);
    l.runProgram();
    ok(a, "a: value=3, 0=0, 1=1, 2=2, 3=3");
    ok(b, "b: value=0, 0=0, 1=0, 2=0, 3=0");

    l.clearProgram(); b.move(1, a, 2); l.runProgram();
    ok(b, "b: value=0, 0=0, 1=2, 2=0, 3=0");
    l.clearProgram(); a.move(0, b, 1); l.runProgram();
    ok(a, "a: value=3, 0=2, 1=1, 2=2, 3=3");

    b.value = 9; l.clearProgram(); a.move(1, b); l.runProgram();
    ok(b, "b: value=9, 0=0, 1=2, 2=0, 3=0");
    ok(a, "a: value=3, 0=2, 1=9, 2=2, 3=3");
   }

  protected static void test_verilog()
   {Layout l = new Layout("""
a var 16
b var 16
c var 16
N var 16
""");

    Field a = l.locateFieldByName("a");
    Field b = l.locateFieldByName("b");
    Field c = l.locateFieldByName("c");
    Field N = l.locateFieldByName("N");

    l.clearProgram();
    N.iWrite(10);
    a.iWrite(0);
    b.iWrite(1);
    l.P.new For(N)
     {void code()
       {c.iAdd(a, b);
        a.iMove(b);
        b.iMove(c);
       }
     };
    l.runProgram();
    ok(a, "a: value=55");
    ok(b, "b: value=89");
    ok(c, "c: value=89");
    l.P.generateVerilog();
   }

  protected static void oldTests()                                              // Tests thought to be in good shape
   {test_parse();
    test_parse_top();
    test_vars();
    test_array();
    test_add();
    test_half();
    test_if();
    test_block();
    test_for();
    test_stop();
    test_variable();
    test_stackProgram();
    test_move();
    test_verilog();
   }

  protected static void newTests()                                              // Tests being worked on
   {oldTests();
    //test_verilog();
   }

  public static void main(String[] args)                                        // Test if called as a program
   {try                                                                         // Get a traceback in a format clickable in Geany if something goes wrong to speed up debugging.
     {if (github_actions) oldTests(); else newTests();                          // Tests to run
      if (github_actions)                                                       // Coverage analysis
       {coverageAnalysis(12, "Test.java");                                      // Used for printing
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
