package sos;

import java.util.*;

/**
 * This class is the centerpiece of a simulation of the essential hardware of a
 * microcomputer.  This includes a processor chip, RAM and I/O devices.  It is
 * designed to demonstrate a simulated operating system (SOS).
 * 
 * Notation: [arg1] means, "the value stored in register arg1".
 * 			 arg1 means, "the literal value of arg1".
 *
 * @see RAM
 * @see SOS
 * @see Program
 * @see Sim
 */

public class CPU implements Runnable
{
    
    //======================================================================
    //Constants
    //----------------------------------------------------------------------

    //These constants define the instructions available on the chip
    public static final int SET    = 0;    /* set value of reg */
    public static final int ADD    = 1;    // put reg1 + reg2 into reg3
    public static final int SUB    = 2;    // put reg1 - reg2 into reg3
    public static final int MUL    = 3;    // put reg1 * reg2 into reg3
    public static final int DIV    = 4;    // put reg1 / reg2 into reg3
    public static final int COPY   = 5;    // copy reg1 to reg2
    public static final int BRANCH = 6;    // goto address in reg
    public static final int BNE    = 7;    // branch if not equal
    public static final int BLT    = 8;    // branch if less than
    public static final int POP    = 9;    // load value from stack
    public static final int PUSH   = 10;   // save value to stack
    public static final int LOAD   = 11;   // load value from heap
    public static final int SAVE   = 12;   // save value to heap
    public static final int TRAP   = 15;   // system call
    
    //These constants define the indexes to each register
    public static final int R0   = 0;     // general purpose registers
    public static final int R1   = 1;
    public static final int R2   = 2;
    public static final int R3   = 3;
    public static final int R4   = 4;
    public static final int PC   = 5;     // program counter
    public static final int SP   = 6;     // stack pointer
    public static final int BASE = 7;     // bottom of currently accessible RAM
    public static final int LIM  = 8;     // top of accessible RAM
    public static final int NUMREG = 9;   // number of registers

    //Misc constants
    public static final int NUMGENREG = PC; // the number of general registers
    public static final int INSTRSIZE = 4;  // number of ints in a single instr +
                                            // args.  (Set to a fixed value for simplicity.)

    public static final int CLOCK_FREQ = 100; // how many instructions per clock interrupt.
    //======================================================================
    //Member variables
    //----------------------------------------------------------------------
    /**
     * specifies whether the CPU should output details of its work
     **/
    private boolean m_verbose = false;

    /**
     * This array contains all the registers on the "chip".
     **/
    private int m_registers[];

    /**
     * A pointer to the RAM used by this CPU
     *
     * @see RAM
     **/
    private RAM m_RAM = null;
    
    /**
     * a reference to the trap handler for this CPU.  On a real CPU this would
     * simply be an address that the PC register is set to.
     */
    private TrapHandler m_TH = null;
    
    /**
     * A reference to the interrupt handler for the CPU.
     * On a real CPU this is probably a separate piece of hardware
     *  / Address of it.
     */
    
    private InterruptController m_IC = null;
    
    /**
     * A reference to the memory management unit. It is hardware
     * that helps to translate logical to physical addresses. 
     */
    
    private MMU m_MMU = null;
    
    /**
     * The number of 'ticks' the CPU has run.
     */
    private int m_ticks = 1;

    //======================================================================
    //Methods
    //----------------------------------------------------------------------

    /**
     * CPU ctor
     *
     * Intializes all member variables.
     */
    public CPU(RAM ram, InterruptController ic, MMU theUnit)
    {
        m_registers = new int[NUMREG];
        for(int i = 0; i < NUMREG; i++)
        {
            m_registers[i] = 0;
        }
        m_RAM = ram;
        m_IC = ic;
        m_MMU = theUnit;

    }//CPU ctor

    /**
     * getPC
     *
     * @return the value of the program counter
     */
    public int getPC()
    {
        return m_registers[PC];
    }

    /**
     * getSP
     *
     * @return the value of the stack pointer
     */
    public int getSP()
    {
        return m_registers[SP];
    }

    /**
     * getBASE
     *
     * @return the value of the base register
     */
    public int getBASE()
    {
        return m_registers[BASE];
    }

    /**
     * getLIMIT
     *
     * @return the value of the limit register
     */
    public int getLIM()
    {
        return m_registers[LIM];
    }

    /**
     * getRegisters
     *
     * @return the registers
     */
    public int[] getRegisters()
    {
        return m_registers;
    }

    /**
     * getTicks
     * @return the number of ticks since the CPU booted up.
     */
    public int getTicks(){ return m_ticks; }
    
    public void addTicks(int ticksToAdd)
    {
    	// Add the ticks.
    	// If we exceed the maximum value for an int,
    	// Reset to zero.
    	m_ticks = m_ticks + ticksToAdd;
		// If we overflow, reset to zero.
		if(m_ticks < 0) {m_ticks = 0;}
    }
    
    /**
     * setPC
     *
     * @param v the new value of the program counter
     */
    public void setPC(int v)
    {
        m_registers[PC] = v;
    }

    /**
     * setSP
     *
     * @param v the new value of the stack pointer
     */
    public void setSP(int v)
    {
        m_registers[SP] = v;
    }

    /**
     * setBASE
     *
     * @param v the new value of the base register
     */
    public void setBASE(int v)
    {
        m_registers[BASE] = v;
    }

    /**
     * setLIM
     *
     * @param v the new value of the limit register
     */
    public void setLIM(int v)
    {
        m_registers[LIM] = v;
    }

    /**
     * regDump
     *
     * Prints the values of the registers.  Useful for debugging.
     */
    protected void regDump()
    {
        for(int i = 0; i < NUMGENREG; i++)
        {
            System.out.print("r" + i + "=" + m_registers[i] + " ");
        }//for
        System.out.print("PC=" + m_registers[PC] + " ");
        System.out.print("SP=" + m_registers[SP] + " ");
        System.out.print("BASE=" + m_registers[BASE] + " ");
        System.out.print("LIM=" + m_registers[LIM] + " ");
        System.out.println("");
    }//regDump

    /**
     * printIntr
     *
     * Prints a given instruction in a user readable format.  Useful for
     * debugging.
     *
     * @param instr the current instruction
     */
    public void printInstr(int[] instr)
    {
            switch(instr[0])
            {
                case SET:
                    System.out.println("SET R" + instr[1] + " = " + instr[2]);
                    break;
                case ADD:
                    System.out.println("ADD R" + instr[1] + " = R" + instr[2] + " + R" + instr[3]);
                    break;
                case SUB:
                    System.out.println("SUB R" + instr[1] + " = R" + instr[2] + " - R" + instr[3]);
                    break;
                case MUL:
                    System.out.println("MUL R" + instr[1] + " = R" + instr[2] + " * R" + instr[3]);
                    break;
                case DIV:
                    System.out.println("DIV R" + instr[1] + " = R" + instr[2] + " / R" + instr[3]);
                    break;
                case COPY:
                    System.out.println("COPY R" + instr[1] + " = R" + instr[2]);
                    break;
                case BRANCH:
                    System.out.println("BRANCH @" + instr[1]);
                    break;
                case BNE:
                    System.out.println("BNE (R" + instr[1] + " != R" + instr[2] + ") @" + instr[3]);
                    break;
                case BLT:
                    System.out.println("BLT (R" + instr[1] + " < R" + instr[2] + ") @" + instr[3]);
                    break;
                case POP:
                    System.out.println("POP R" + instr[1]);
                    break;
                case PUSH:
                    System.out.println("PUSH R" + instr[1]);
                    break;
                case LOAD:
                    System.out.println("LOAD R" + instr[1] + " <-- @R" + instr[2]);
                    break;
                case SAVE:
                    System.out.println("SAVE R" + instr[1] + " --> @R" + instr[2]);
                    break;
                case TRAP:
                    System.out.print("TRAP ");
                    break;
                default:        // should never be reached
                    System.out.println("?? ");
                    break;          
            }//switch

    }//printInstr


    /**
     * run
     * 
     * This is the method that simulates the CPU.
     */
    public void run()
    {
    	
        int[] instruction = new int[4];
    	while(true)
    	{
    		// Check for new Data
    		this.checkForIOInterrupt();
    		
    		/*
    		// Check to see if we need to clockInterrupt()
    		if (0 == m_ticks % CPU.CLOCK_FREQ)
    		{
    			this.m_TH.interruptClock();
    		}
    		
			m_ticks += 1;
			// If we overflow, reset to zero.
			if(m_ticks < 0) {m_ticks = 0;}
			*/
    		
    		if(!this.m_TH.updateProcessTimes())
    		{
    			this.m_TH.interruptClock();
    		}
    		m_ticks += 1;
    		
    		if(m_ticks < 0) { m_ticks = 0; }
    		
    		// Get the next instruction and increment the PC.
    		instruction = this.m_MMU.fetch(this.getPC() + this.getBASE());
    		this.setPC(this.getPC() + CPU.INSTRSIZE);
    		
    		// If we're in Debug mode, print some helpful things.
    		if(this.m_verbose)
    		{
    			this.regDump();
    			this.printInstr(instruction);
    		}
    		// Decode the instruction and do stuff based on what is requested.
    	switch(instruction[0])
			{
    		// set [arg1] = arg2. 
	    	case SET:
	    		if( isValidRegister(instruction[1]) )
	    			{
	    				this.m_registers[instruction[1]] = instruction[2];
	    			}
	    		else
	    		{
	    			m_TH.interruptIllegalInstruction(instruction);
	    		}
	    		break;
	    		
    		// ADD: [arg1] = [arg2] + [arg3]
	    	case ADD:
	    		if(isValidRegister(instruction[1]) && isValidRegister(instruction[2])
	    				&& isValidRegister(instruction[3]))
	    		{
	    			this.m_registers[instruction[1]] = this.m_registers[instruction[2]] + 
	    												this.m_registers[instruction[3]];
	    		}
	    		else
	    		{
	    			m_TH.interruptIllegalInstruction(instruction);
	    		}
	    		break;
	    		
    		// SUB: [arg1] = [arg2] - [arg3]
	    	case SUB:
	    		if(isValidRegister(instruction[1]) && isValidRegister(instruction[2])
	    				&& isValidRegister(instruction[3]))
	    		{
	    			this.m_registers[instruction[1]] = this.m_registers[instruction[2]] - 
	    												this.m_registers[instruction[3]];
	    		}
	    		else
	    		{
	    			m_TH.interruptIllegalInstruction(instruction);
	    		}
	    		break;
	    		
	    		// MUL: [arg1] = [arg2] * [arg3].
	    	case MUL:
	    		if(isValidRegister(instruction[1]) && isValidRegister(instruction[2])
	    				&& isValidRegister(instruction[3]))
	    		{
	    			this.m_registers[instruction[1]] = this.m_registers[instruction[2]] * 
	    												this.m_registers[instruction[3]];
	    		}
	    		else
	    		{
	    			m_TH.interruptIllegalInstruction(instruction);
	    		}
	    		break;
	
	    		//DIV: [arg1] = [arg2] / [arg3].
	    	case DIV:
	    		if(isValidRegister(instruction[1]) && isValidRegister(instruction[2])
	    				&& isValidRegister(instruction[3]))
	    		{
	    			if(0 != this.m_registers[instruction[3]])	
	    			{
	    				this.m_registers[instruction[1]] = this.m_registers[instruction[2]] / 
	    												this.m_registers[instruction[3]];
	    			}
	    			else
	    			{
	    				m_TH.interruptDivideByZero();
	    			}
	    		}
	    		else
	    		{
	    			m_TH.interruptIllegalInstruction(instruction);
	    		}
	    		break;
	    		
	    		// COPY: [arg1] = [arg2].
	    	case COPY:
	    		if( isValidRegister(instruction[1]) && isValidRegister(instruction[2]) )
    			{
    				this.m_registers[instruction[1]] = this.m_registers[instruction[2]];
    			}
	    		else
	    		{
	    			m_TH.interruptIllegalInstruction(instruction);
	    		}
	    		break;
	    		
	    		// BRANCH: Set the Program counter to the literal in arg1 ( [PC] = arg1).
	    	case BRANCH:

    			if(this.isValidMemoryAccess(this.logicalToPhysicalAddress(instruction[1])))
    			{
    				this.m_registers[CPU.PC] = instruction[1];
    			}
    			else
    			{
    				m_TH.interruptIllegalMemoryAccess(instruction[1]);
    			}
	    		break;
	    		
	    		// BNE: Branch to the address at arg3 if [arg1] != [arg2].
	    	case BNE:
	    		if( isValidRegister(instruction[1]) && isValidRegister(instruction[2]) )
    			{
	    			if(this.m_registers[instruction[1]] != this.m_registers[instruction[2]])
	    			{
	    				if(this.isValidMemoryAccess(this.logicalToPhysicalAddress(instruction[3])))
	    				{
	    					this.m_registers[CPU.PC] = instruction[3];
	    				}
	        			else
	        			{
	        				m_TH.interruptIllegalMemoryAccess(instruction[3]);
	        			}
	    			}
	    		
    			}
	    		break;
	    		
	    		// BLT: If [arg1] is less than [arg2], set [PC] to arg3.
	    	case BLT:
	    		if( isValidRegister(instruction[1]) && isValidRegister(instruction[2]) )
    			{
	    			if(this.m_registers[instruction[1]] < this.m_registers[instruction[2]])
	    			{
	    				if(this.isValidMemoryAccess(this.logicalToPhysicalAddress(instruction[3])))
	    				{
	    					this.m_registers[CPU.PC] = instruction[3];
	    				}
	        			else
	        			{
	        				m_TH.interruptIllegalMemoryAccess(instruction[3]);
	        			}
	    			}
    			}
	    		break;
	    		
	    		// Read [arg1] from the stack and decrement the stack pointer.
	    	case POP:
	    		if(isValidRegister(instruction[1]))
	    		{
	    			if(0 == this.getSP())
	    			{
	    				System.out.println("WHY AM I HERE?!");
	    			}
	    			this.m_registers[instruction[1]] = pop();
	    		}
	    		break;
	    		
	    		// increment the stack pointer and store [arg1] to the stack.
	    	case PUSH:
	    		if(isValidRegister(instruction[1]))
	    		{
	    			push(m_registers[instruction[1]]);
	    		}
	    		break;
	    		
	    		// [arg1] = The value in RAM at physical address [arg2].
	    	case LOAD:
	    		if(isValidRegister(instruction[1]) && isValidRegister(instruction[2]))
	    		{
	    			if(this.isValidMemoryAccess(this.m_registers[instruction[2]] + this.getBASE()))
	    			{
	    				this.m_registers[instruction[1]] = 
	    						this.m_MMU.read(this.m_registers[instruction[2]] + this.getBASE());
	    			}
	    			else
	    			{
	    				m_TH.interruptIllegalMemoryAccess(this.m_registers[instruction[2]]);
	    			}	
    			}
	    		break;
	    		
	    		// The value in RAM at physical address [arg2] = [arg1]
	    	case SAVE:
	    		if(isValidRegister(instruction[1]) && isValidRegister(instruction[2]))
	    		{
	    			if(this.isValidMemoryAccess(this.m_registers[instruction[2]] + this.getBASE()))
	    			{
	    				this.m_MMU.write(this.m_registers[instruction[2]] + this.getBASE(),
	    						this.m_registers[instruction[1]]);
	    			}
	    			else
	    			{
	    				m_TH.interruptIllegalMemoryAccess(this.m_registers[instruction[2]]);
	    			}
    			}
	    		break;
	    		
	    	case TRAP:
	    		m_TH.systemCall();
			}
    		
    	}
    	
        
    }//run

	/**
	 * @param instruction
	 */
	protected void push(int value) {
		if(this.isValidMemoryAccess(this.getLIM() - this.getSP() - 1))
		{
			this.changeSP(1);
			this.m_MMU.write(this.getLIM() - this.getSP(), value);
		}
	}

	/**
	 * Pops an element from the stack.
	 * 
	 * 
	 * @param instruction
	 */
	protected int pop() {
		
			if(0 < this.getSP())
			{
				int retVal = this.m_MMU.read(this.getLIM() - this.getSP());
				this.changeSP(-1);
				return retVal;
			}
			// If we try to pop an empty stack, throw an illegalMemoryAccess interrupt.
			m_TH.interruptIllegalMemoryAccess(getLIM());
			// This should never get called.
			System.out.println("ERROR IN pop() IN CPU.JAVA! This line should never get called.");
			return 0;
		
	}
    
	
	/**
	 * A helper method for determining if a register ID is valid.
	 * 
	 * @param register
	 * @return whether or not the register is valid (General-purpose).
	 */
    private static boolean isValidRegister(int register)
    {
    	if(0 <= register && NUMGENREG > register)
    	{
    		return true;
    	}
    	//else: handle invalid register wherever you call this function.
    	// (not handled here because we do not have the entire instruction to pass
    	// to the illegalInstruction interrupt).
    	return false;
    }
    
    /**
     * A helper method to change the stack pointer that guards against invalid memory access.
     * 
     * @param delta
     * @return Success or failure of stack-pointer change attempt
     */
    private boolean changeSP(int delta)
    {
    	if (this.isValidMemoryAccess(this.logicalToPhysicalAddress(this.getSP() + delta)))
    	{
    		this.m_registers[CPU.SP] += delta;
    		return true;
    	}
		return false;	
    }
    
    /**
     * Converts a logical address to a physical one.
     * 
     * @param logicalAddress
     * @return the physical address.
     */
    protected int logicalToPhysicalAddress(int logicalAddress)
    {
    	// convert to physical address.
    	return logicalAddress + this.getBASE();
    }
    
    /**
     * Helper method that checks if a memory-access attempt is accessing valid memory.
     * @param physicalAddress
     * @return True if valid. False if invalid.
     */
    protected boolean isValidMemoryAccess(int physicalAddress)
    {
    	// Error checking
    	if(physicalAddress >= this.getBASE() && physicalAddress < this.getLIM())
    	{
    		return true;
    	}
    	return false;
    }
    
    //======================================================================
    //Callback Interface
    //----------------------------------------------------------------------
    /**
     * TrapHandler
     *
     * This interface should be implemented by the operating system to allow the
     * simulated CPU to generate hardware interrupts and system calls.
     */
    public interface TrapHandler
    {
        void interruptIllegalMemoryAccess(int addr);
        void interruptDivideByZero();
        void interruptIllegalInstruction(int[] instr);
        void interruptClock();
        public boolean updateProcessTimes();
        public void interruptIOReadComplete(int devID, int addr, int data);
        public void interruptIOWriteComplete(int devID, int addr);
        void systemCall();
    };//interface TrapHandler


    /**
     * registerTrapHandler
     *
     * allows SOS to register itself as the trap handler 
     */
    public void registerTrapHandler(TrapHandler th)
    {
        m_TH = th;
        m_MMU.registerTrapHandler(th);
    }
    
    /**
     * checkForIOInterrupt
     *
     * Checks the databus for signals from the interrupt controller and, if
     * found, invokes the appropriate handler in the operating system.
     *
     */
    private void checkForIOInterrupt()
    {
        //If there is no interrupt to process, do nothing
        if (m_IC.isEmpty())
        {
            return;
        }
        
        //Retreive the interrupt data
        int[] intData = m_IC.getData();

        //Report the data if in verbose mode
        if (m_verbose)
        {
            System.out.println("CPU received interrupt: type=" + intData[0]
                               + " dev=" + intData[1] + " addr=" + intData[2]
                               + " data=" + intData[3]);
        }

        //Dispatch the interrupt to the OS
        switch(intData[0])
        {
            case InterruptController.INT_READ_DONE:
                m_TH.interruptIOReadComplete(intData[1], intData[2], intData[3]);
                break;
            case InterruptController.INT_WRITE_DONE:
                m_TH.interruptIOWriteComplete(intData[1], intData[2]);
                break;
            default:
                System.out.println("CPU ERROR:  Illegal Interrupt Received.");
                System.exit(-1);
                break;
        }//switch

    }//checkForIOInterrupt

    
    
    
};//class CPU
