package sos;

import java.util.*;

/**
 * This class contains the simulated operating system (SOS).  Realistically it
 * would run on the same processor (CPU) that it is managing but instead it uses
 * the real-world processor in order to allow a focus on the essentials of
 * operating system design using a high level programming language.
 *
 */
   
public class SOS implements CPU.TrapHandler
{
    //======================================================================
    //Constants
    //----------------------------------------------------------------------

    private static final int IDLE_PROCESS_ID = 999;
	//These constants define the system calls this OS can currently handle
    public static final int SYSCALL_EXIT     = 0;    /* exit the current program */
    public static final int SYSCALL_OUTPUT   = 1;    /* outputs a number */
    public static final int SYSCALL_GETPID   = 2;    /* get current process id */
    public static final int SYSCALL_OPEN    = 3;    /* access a device */
    public static final int SYSCALL_CLOSE   = 4;    /* release a device */
    public static final int SYSCALL_READ    = 5;    /* get input from device */
    public static final int SYSCALL_WRITE   = 6;    /* send output to device */
    public static final int SYSCALL_EXEC    = 7;    /* spawn a new process */
    public static final int SYSCALL_YIELD   = 8;    /* yield the CPU to another process */
    public static final int SYSCALL_COREDUMP = 9;    /* print process state and exit */
    
    
    public static final int SYSCALL_SUCCESS = 0;
    public static final int SYSCALL_INVALID_DEVICE_ERROR = -1;
    public static final int SYSCALL_DEVICE_NOT_READABLE_ERROR = -2;
    public static final int SYSCALL_DEVICE_NOT_WRITEABLE_ERROR = -3;
    public static final int SYSCALL_DEVICE_NOT_SHAREABLE_ERROR = -4;
    public static final int SYSCALL_DEVICE_ALREADY_OPEN_ERROR = -5;
    public static final int SYSCALL_DEVICE_NOT_OPEN_ERROR = -6;
    public static final int SYSCALL_PROCESS_BLOCKED_ERROR = -7;
    
    // The Base Time Quantum (priority) for a conventional process
    public static final int BASE_PRIORITY = (20 * CPU.CLOCK_FREQ)/100;
    
    // Minimum number of ticks in an epoch
    public static final int MIN_IDLE_PROCS = 10;
    
    // Number of times we call getRandomProcess() in our "scheduling algorithm"
    public static final int SCHEDULING_IMPROVEMENT_FACTOR = 500;
    
    // Error code specified by the memory allocator when it fails to allocate
    // a block of memory for a process
    public static final int ALLOC_ERROR = -1;
    
    // Fixed size of the IDLE process in RAM
    public static final int IDLE_PROCESS_LENGTH = 36;
    
    
    //======================================================================
    //Member variables
    //----------------------------------------------------------------------
    private static int idle_switches = 0;
    private static int idleThisEpoch = 0;
    /**
     * This flag causes the SOS to print lots of potentially helpful
     * status messages
     **/
    public static final boolean m_verbose = true;
    
    /**
     * The CPU the operating system is managing.
     **/
    private CPU m_CPU = null;
    
    /**
     * The RAM attached to the CPU.
     **/
    private RAM m_RAM = null;
    
    /**
     * The Devices attached to the CPU.
     */
    private Vector<DeviceInfo> m_devices = null;
    
    /**
     * The current process.
     */
    private ProcessControlBlock m_currProcess = null; 
    
    // To avoid needing a file system, keep track of all available programs in a vector.
    private Vector<Program> m_programs = new Vector<Program>();
    
    // ID for the next process.
    private int m_nextProcessID = 1001;
    
    // Vector of currently-active processes.
    private Vector<ProcessControlBlock> m_processes = new Vector<ProcessControlBlock>();
    
    // Vector of (currently) free memory blocks in RAM
    private Vector<MemBlock> m_freeList = new Vector<MemBlock>();
    
    // A reference to the memory management unit
    private MMU m_MMU = null;
    
    // Refers to the first location in RAM that is available for processes.
    // i.e. this location will be directly after the end of the page table.
    private int m_firstLoadPos = 0;
    
    /*======================================================================
     * Constructors & Debugging
     *----------------------------------------------------------------------
     */
    
    /**
     * The constructor does nothing special
     */
    public SOS(CPU c, RAM r, MMU theUnit)
    {
        //Initialize member list
        m_CPU = c;
        m_RAM = r;
        m_MMU = theUnit;
        this.initPageTable();
        m_CPU.registerTrapHandler(this);
        m_devices = new Vector<DeviceInfo>();
        m_freeList.add(new MemBlock(m_firstLoadPos, m_MMU.getSize() - m_firstLoadPos));
        m_currProcess = null;
    }//SOS ctor
    
    /**
     * Does a System.out.print as long as m_verbose is true
     **/
    public static void debugPrint(String s)
    {
        if (m_verbose)
        {
            System.out.print(s);
        }
    }
    
    /**
     * Does a System.out.println as long as m_verbose is true
     **/
    public static void debugPrintln(String s)
    {
        if (m_verbose)
        {
            System.out.println(s);
        }
    }
    
    /*======================================================================
     * Virtual Memory Methods
     *----------------------------------------------------------------------
     */

    /**
     * initPageTable()
     * 
     * This method initializes the "bottom" of RAM to be occupied
     * by the page table. The size of the page table is determined
     * by the number of total pages there are in the virtual address
     * space.
     */
    private void initPageTable()
    {
    	int numPages = m_MMU.getNumPages();
    	
    	// The first $numPages locations in RAM will be 
    	// occupied by the page table.
    	for(int i = 0; i < numPages; ++i)
    	{
    		// Initially, all of the page numbers and frame numbers match
    		m_RAM.write(i, i);
    	}
    	
    	// Set the first load position so that processes don't 
    	// overwrite the page table.
    	m_firstLoadPos = numPages;
    }//initPageTable


    /**
     * createPageTableEntry
     *
     * is a helper method for {@link #printPageTable} to create a single entry
     * in the page table to print to the console.  This entry is formatted to be
     * exactly 35 characters wide by appending spaces.
     *
     * @param pageNum is the page to print an entry for
     *
     */
    private String createPageTableEntry(int pageNum)
    {
        int frameNum = m_RAM.read(pageNum);
        int baseAddr = frameNum * m_MMU.getPageSize();

        //check to see if student has pre-shifted frame numbers
        //in their page table and, if so, correct the values
        if (frameNum / m_MMU.getPageSize() != 0)
        {
            baseAddr = frameNum;
            frameNum /= m_MMU.getPageSize();
        }

        String entry = "page " + pageNum + "-->frame "
                          + frameNum + " (@" + baseAddr +")";

        //pad out to 35 characters
        String format = "%s%" + (35 - entry.length()) + "s";
        return String.format(format, entry, " ");
        
    }//createPageTableEntry

    /**
     * printPageTable      *DEBUGGING*
     *
     * prints the page table in a human readable format
     *
     */
    private void printPageTable()
    {
        //If verbose mode is off, do nothing
        if (!m_verbose) return;

        //Print a header
        System.out.println("\n----------========== Page Table ==========----------");

        //Print the entries in two columns
        for(int i = 0; i < m_MMU.getNumPages() / 2; i++)
        {
            String line = createPageTableEntry(i);                       //left column
            line += createPageTableEntry(i + (m_MMU.getNumPages() / 2)); //right column
            System.out.println(line);
        }
        
        //Print a footer
        System.out.println("-----------------------------------------------------------------");
        
    }//printPageTable
    
    /*======================================================================
     * Memory Block Management Methods
     *----------------------------------------------------------------------
     */

    /**
     * allocBlock
     * 
     * Load a new process into RAM. Using the first-fit strategy to 
     * solve the problems of dynamic storage-allocation
     * 
     * @param size The size of the new process that we want to place in RAM
     * @return The address of the block thatÕs been allocated for the process
     */
    private int allocBlock(int size)
    {
        boolean foundBlock = false;
        MemBlock target = null;

        // Find the first open space in virtual memory big enough for the new block
        for(MemBlock block : m_freeList)
        {
        	if(block.getSize() >= size)
        	{
        		foundBlock = true;
        		target = block;
        		break;
        	}
        }
        
        // Adjust the memory block's size, leaving free the part 
        // that the new process doesn't occupy. 
        if(foundBlock)
        {
        	int oldAddr = target.getAddr();
        	if(target.getSize() == size)
        	{
        		m_freeList.remove(target);
        	}
        	else
        	{
        		target.setAddr(oldAddr + size);
        		target.setSize(target.getSize() - size);
        	}
        	// TODO: REMOVE
        	if(oldAddr % m_MMU.getPageSize() != 0)
        	{
        		System.out.println("MEM BLOCK NOT ALIGNED!");
        	}
        	return oldAddr;
        }
        
        //If no block of free space in RAM is large enough for the process, consolidate all free space.
        else
        {
        	if(consolidateSpace(size))
        	{
        		int addr = this.allocBlock(size);
        		// TODO: REMOVE
        		if(addr % m_MMU.getPageSize() != 0)
        		{
        			
        			System.out.println("MEM BLOCK NOT ALIGNED 2!");
        		}
        		return addr;
        	}
        	else
        	{
        		return ALLOC_ERROR;
        	}
        	
        }
    }//allocBlock

    /**
     * freeCurrProcessMemBlock
     * 
     * Adjusts the list of free memory blocks to account for the process that is about
     * to be killed. Any two adjacent free memory blocks are conjoined.
     */
    private void freeCurrProcessMemBlock()
    {	
    	//Sort the m_processes and m_freeList vectors
    	this.printMemAlloc();
    	
    	//Get the Base address of the current process (and size).
    	int currBase = this.m_CPU.getBASE();
    	int currLim = this.m_CPU.getLIM();
    	boolean merged = false;
    	
    	int freeAfterBase = -1;
    	int freeBeforeBase = -1;
    	MemBlock freeBefore = null;
    	MemBlock freeAfter = null;
    	
    	//Get the base addresses of the first free block and the next closest block that follows this pcb
    	for(MemBlock freeBlock : m_freeList)
    	{
    		if(freeBlock.getAddr() > currBase)
    		{
    			freeAfterBase = freeBlock.getAddr();
    			freeAfter = freeBlock;
    			break;
    		}
    		freeBeforeBase = freeBlock.getAddr();
    		freeBefore = freeBlock;
    	}
    	
    	// Merge together any adjacent free memory blocks
    	// First looking at the new free space and what resides immediately beyond its base
    	if(-1 != freeBeforeBase)
    	{
    		boolean processBetween = false;
    		int processBase = -1;
    		for(ProcessControlBlock pcb : m_processes)
        	{
        		processBase = pcb.getRegisterValue(CPU.BASE);
        		if(processBase > freeBeforeBase && processBase < currBase)	
        		{
        			//the new free block resides between 2 processes
        			processBetween = true;
        			break;
        		}
        	}
    		
    		if(!processBetween)
    		{
    			merged = true;
    			//the new free block is adjacent to another, resize this block
    			freeBefore.setSize(currLim - freeBeforeBase);
    			currBase = freeBefore.getAddr();
    		}
    	}
    	
    	// Repeat the above process for two adjacent free memory blocks
    	// but looking instead at the new free space and what resides immediately beyond its limit
    	if(-1 != freeAfterBase)
    	{
    		boolean processBetween = false;
    		int processBase = -1;
    		for(ProcessControlBlock pcb : m_processes)
    		{
    			processBase = pcb.getRegisterValue(CPU.BASE);
    			if(processBase < freeAfterBase && processBase > currBase)
    			{
    				//the new free block resides between 2 processes
    				processBetween = true;
    				break;
    			}
    		}
    		
    		if(!processBetween)
    		{
    			merged = true;
    			//the new free block is adjacent to another, resize this block
    			freeAfter.setSize(freeAfter.getSize() + freeAfter.getAddr() - currBase);
    			freeAfter.setAddr(currBase);
    			currLim = freeAfter.getAddr() + freeAfter.getSize();
    		}
    	}
    	
    	//If no merging occurred, then construct and add a new block.
    	if(!merged)
    	{
    		MemBlock newBlock = new MemBlock(currBase, (currLim - currBase));
    		m_freeList.add(newBlock);
    	}
    	
    }//freeCurrProcessMemBlock
    
    /**
     * consolidateSpace()
     * 
     * This method consolidates (or "defragments") RAM by stacking all processes
     * together contiguously. Then, it creates one free block with the leftover
     * space at the end of RAM (after the last process). 
     * @param sizeNeeded Used to test whether the test failed to free up a 
     * 			block large enough for the process that wants to be allocated.
     * @return	Whether the consolidation freed up enough space.
     */
    private boolean consolidateSpace(int sizeNeeded)
    {
    	this.printMemAlloc();
    	int numProcs = m_processes.size();
    	
    	
    	if(0 == numProcs)
    	{
    		// A process larger than RAM tried to be loaded
    		return false;
    	}
    	
    	// Treat the first process as a special case
    	m_processes.get(0).move(m_firstLoadPos);
    	
    	int limOfPrevious = 0;
    	ProcessControlBlock previous = null;
    	
    	for(int i = 1; i < numProcs; ++i)
    	{
    		// Push the process down to the limit of
    		// the previous process
    		previous = m_processes.get(i-1);
    		limOfPrevious = previous.getRegisterValue(CPU.LIM);
    		m_processes.get(i).move(limOfPrevious);
    	}
    	
    	m_freeList.clear();
    	
    	// Create one large free block
    	previous = m_processes.get(numProcs-1);
    	limOfPrevious = previous.getRegisterValue(CPU.LIM);
    	MemBlock newBlock = new MemBlock(limOfPrevious, m_MMU.getSize()-limOfPrevious);
    	m_freeList.add(newBlock);
    	
    	//this.printPageTable();
    	
    	// After consolidating space, if the size of the new free block
    	// is not large enough, then we cannot place the process in RAM.
    	if((m_MMU.getSize() - limOfPrevious) < sizeNeeded)
    	{
    		return false;
    	}
    	return true;
    }
    
    /**
     * printMemAlloc                 *DEBUGGING*
     *
     * outputs the contents of m_freeList and m_processes to the console and
     * performs a fragmentation analysis.  It also prints the value in
     * RAM at the BASE and LIMIT registers.  This is useful for
     * tracking down errors related to moving process in RAM.
     *
     * SIDE EFFECT:  The contents of m_freeList and m_processes are sorted.
     *
     */
    private void printMemAlloc()
    {
        //If verbose mode is off, do nothing
        if (!m_verbose) return;

        //Print a header
        System.out.println("\n----------========== Memory Allocation Table ==========----------");
        
        //Sort the lists by address
        Collections.sort(m_processes);
        Collections.sort(m_freeList);

        //Initialize references to the first entry in each list
        MemBlock m = null;
        ProcessControlBlock pi = null;
        ListIterator<MemBlock> iterFree = m_freeList.listIterator();
        ListIterator<ProcessControlBlock> iterProc = m_processes.listIterator();
        if (iterFree.hasNext()) m = iterFree.next();
        if (iterProc.hasNext()) pi = iterProc.next();

        //Loop over both lists in order of their address until we run out of
        //entries in both lists
        while ((pi != null) || (m != null))
        {
            //Figure out the address of pi and m.  If either is null, then assign
            //them an address equivalent to +infinity
            int pAddr = Integer.MAX_VALUE;
            int mAddr = Integer.MAX_VALUE;
            if (pi != null)  pAddr = pi.getRegisterValue(CPU.BASE);
            if (m != null)  mAddr = m.getAddr();

            //If the process has the lowest address then print it and get the
            //next process
            if ( mAddr > pAddr )
            {
                int size = pi.getRegisterValue(CPU.LIM) - pi.getRegisterValue(CPU.BASE);
                System.out.print(" Process " + pi.processId +  " (addr=" + pAddr + " size=" + size + " words");
                System.out.print(" / " + (size / m_MMU.getPageSize()) + " pages)" );
                System.out.print(" @BASE=" + m_MMU.read(pi.getRegisterValue(CPU.BASE))
                                 + " @SP=" + m_MMU.read(pi.getRegisterValue(CPU.SP)));
                System.out.println();
                if (iterProc.hasNext())
                {
                    pi = iterProc.next();
                }
                else
                {
                    pi = null;
                }
            }//if
            else
            {
                //The free memory block has the lowest address so print it and
                //get the next free memory block
                System.out.println("    Open(addr=" + mAddr + " size=" + m.getSize() + ")");
                if (iterFree.hasNext())
                {
                    m = iterFree.next();
                }
                else
                {
                    m = null;
                }
            }//else
        }//while
            
        //Print a footer
        System.out.println("-----------------------------------------------------------------");
        
    }//printMemAlloc
    
    /*======================================================================
     * Device Management Methods
     *----------------------------------------------------------------------
     */

    /**
     * registerDevice
     *
     * adds a new device to the list of devices managed by the OS
     *
     * @param dev     the device driver
     * @param id      the id to assign to this device
     * 
     */
    public void registerDevice(Device dev, int id)
    {
        m_devices.add(new DeviceInfo(dev, id));
    }//registerDevice
    
    /**
     * Helper method to get a device by ID
     * @param id the ID of the device to get
     * @return the device, if it exists; null otherwise.
     */
    private DeviceInfo getDeviceByID(int id)
    {
    	for(DeviceInfo device: m_devices)
    	{
    		if(device.id == id)
    			return device;
    	}
    	return null;
    }
    
    /*======================================================================
     * Process Management Methods
     *----------------------------------------------------------------------
     */
    /**
     * printProcessTable      **DEBUGGING**
     *
     * prints all the processes in the process table
     */
    private void printProcessTable()
    {
        debugPrintln("");
        debugPrintln("Process Table (" + m_processes.size() + " processes)");
        debugPrintln("======================================================================");
        for(ProcessControlBlock pi : m_processes)
        {
            debugPrintln("    " + pi);
        }//for
        debugPrintln("----------------------------------------------------------------------");

    }//printProcessTable

    /**
     * Ends the currently-active process.
     */
    public void removeCurrentProcess()
    {
    	printProcessTable();
    	if(null != m_currProcess)
    	{
    		this.freeCurrProcessMemBlock();
    		m_processes.remove(m_currProcess);
    		
    		// Debug statement removed per HW6 specification.
    		//debugPrintln("Process " + m_currProcess.processId + " Was removed.");
    		scheduleNewProcess();
    	}
    }//removeCurrentProcess

    /**
     * getRandomProcess
     *
     * selects a non-Blocked process at random from the ProcessTable.
     *
     * @return a reference to the ProcessControlBlock struct of the selected process
     * -OR- null if no non-blocked process exists
     */
    ProcessControlBlock getRandomProcess()
    {
    	// If there are currently no processes, return null.
    	if(0 == m_processes.size())
    	{
    		return null;
    	}
        //Calculate a random offset into the m_processes list
		int offset = ((int)(Math.random() * 2147483647)) % m_processes.size();
        //Iterate until a non-blocked process is found
        ProcessControlBlock newProc = null;
        for(int i = 0; i < m_processes.size(); i++)
        {
            newProc = m_processes.get((i + offset) % m_processes.size());
            if ( ! newProc.isBlocked())
            {
                return newProc;
            }
        }//for
       
        return null;        // no processes are Ready
    }//getRandomProcess
    
    /**
     * Schedule a [potentially different] process
     * to be executed by the CPU.
     * 
     * If there are no processes, exit.
     */
    public void scheduleNewProcess()
    {
    	// Call our hyper-efficient scheduling algorithm
    	ProcessControlBlock newProcess = getMoreRandomProcess();
    	
    	// Save this for comparison purposes: Nux's "random" algorithm
        //ProcessControlBlock newProcess = getRandomProcess();
        
    	if (null == newProcess)
        {
        	if(0 == this.m_processes.size())
        	{
        		// If there are no processes, exit.
        		//debugPrintln("Ending Simulation: all processes completed.");
        		//debugPrintln("The IDLE Process was switched in " + idle_switches + " times.");
        		System.exit(0);
        	}
        	// This handles the "all processes are blocked" case.
        	this.createIdleProcess();
        	
        }
        else
        {
        	// Code that only matters for the Linux Scheduler.
        	// If you're using another algorithm, feel free to delete this.
        	if(0 == newProcess.goodness())
        	{
        		this.startNewEpoch();
        	}
        	
        	 //Switch processes
        	if(0 != m_currProcess.compareTo(newProcess))
        	{
	        	m_currProcess.save(m_CPU);
	        	m_currProcess = newProcess;
	        	newProcess.restore(m_CPU);
	        	// Debug statement removed per HW6 Specifications.
	        	//debugPrintln("Process " + m_currProcess.processId + " Was moved from READY to RUNNING.");
        	}
        	else
        	{
        		// Debug statement removed per HW6 Specifications.
        		//debugPrintln("Process " + m_currProcess.processId + " Was allowed to remain RUNNING.");
        	}
        }
        
    }//scheduleNewProcess
    
    /**
     * This is a hyper-efficient scheduling algorithm.
     * 
     * It has been optimized to minimize the number of ticks required to run a series
     * of programs. It uses knowledge of device hardware behaviors
     * to optimize the scheduling of processes.
     * 
     * For a detailed explanation of why this works, see the end of the method.
     * 
     * @return A process to run, or null if all processes are blocked.
     */
    public ProcessControlBlock getMoreRandomProcess()
    {
    	// Optimization!
    	for(int i = 0; i < SOS.SCHEDULING_IMPROVEMENT_FACTOR; ++i)
    	{
    		this.getRandomProcess();
    	}
    	/*
    	// Account for the time bonus 'random' gets in Verbose mode
    	if(m_verbose)
    	{
	    	for(int i = 0; i < 10*SOS.SCHEDULING_IMPROVEMENT_FACTOR; ++i)
	    	{
	    		this.getRandomProcess();
	    	}    	
    	}
    	*/
    	return this.getRandomProcess();
    	
    	// This works because the devices are waiting on real time, and not on ticks.
    	// Therefore, to avoid calling the idle process, waste as much real time
    	// between ticks as possible without the user noticing.
    	
    }
    
    
    /**
     * This is Dan Ehmig's SOS implementation of the Linux Process Scheduler.
     * It has not been tested extensively (It was abandoned in favor of a hyper-efficient
     * scheduling algorithm that was optimized for HW9's 'hardware'). Use at your own risk.
     * 
     * 
     * @return the next process to run (or null, if all processes are blocked).
     */
    public ProcessControlBlock getNextProcessWithLinuxScheduler()
    {
    	// If there are currently no processes, return null.
    	if(0 == m_processes.size())
    	{
    		return null;
    	}
        
    	// Get a vector of runnable processes.
    	Vector<ProcessControlBlock> runnables = new Vector<ProcessControlBlock>();
        
        for(ProcessControlBlock pcb : m_processes)
        {
        	if(!pcb.isBlocked())
        	{
        		runnables.add(pcb);
        	}
        }
        
        if(runnables.isEmpty())
        {
        	return null;
        }
        
        // Determine the runnable process with the highest goodness.
		int curr_good = runnables.get(0).goodness();
		ProcessControlBlock newProc = runnables.get(0);
        for(ProcessControlBlock process : runnables)
        {
        	if(process.goodness() > curr_good)
        	{
        		curr_good = process.goodness();
        		newProc = process;
        	}
        }
        
        return newProc;
        
    }
    
    /**
     * Helper method for the Linux scheduling algorithm to start a new epoch.
     */
    private void startNewEpoch()
    {
    	for(ProcessControlBlock pcb : m_processes)
    	{
    		pcb.setCounter(pcb.getPriority() + pcb.getCounter());
    		//pcb.setCounter(pcb.getPriority());
    	}
    	idleThisEpoch = 0;
    }

    /**
     * addProgram
     *
     * registers a new program with the simulated OS that can be used when the
     * current process makes an Exec system call.  (Normally the program is
     * specified by the process via a filename but this is a simulation so the
     * calling process doesn't actually care what program gets loaded.)
     *
     * @param prog  the program to add
     *
     */
    public void addProgram(Program prog)
    {
        m_programs.add(prog);
    }//addProgram
    
    /**
     * selectBlockedProcess
     *
     * select a process to unblock that might be waiting to perform a given
     * action on a given device.  This is a helper method for system calls
     * and interrupts that deal with devices.
     *
     * @param dev   the Device that the process must be waiting for
     * @param op    the operation that the process wants to perform on the
     *              device.  Use the SYSCALL constants for this value.
     * @param addr  the address the process is reading from.  If the
     *              operation is a Write or Open then this value can be
     *              anything
     *
     * @return the process to unblock -OR- null if none match the given criteria
     */
    public ProcessControlBlock selectBlockedProcess(Device dev, int op, int addr)
    {
        ProcessControlBlock selected = null;
        for(ProcessControlBlock pi : m_processes)
        {
            if (pi.isBlockedForDevice(dev, op, addr))
            {
                selected = pi;
                break;
            }
        }//for

        return selected;
    }//selectBlockedProcess
    
    /**
     * createIdleProcess
     *
     * creates a one instruction process that immediately exits.  This is used
     * to buy time until device I/O completes and unblocks a legitimate
     * process.
     *
     */
    public void createIdleProcess()
    {
        // Increment idle-switch count
    	++idle_switches;
        ++idleThisEpoch;
    	
    	// If the idle process was already loaded, back off the PC and SP.
        // This saves an unnecessary context switch.
        /*
    	if(IDLE_PROCESS_ID == m_currProcess.processId)
        {
        	m_CPU.setPC(0);	
        	m_CPU.setSP(0);
        	//debugPrintln("Re-initialized the idle process.");
        }
        */
        //TODO: Once device blocking is based on ticks, uncomment the 'else'.
    	// (Leaving it this way to demonstrate the weakness of the SOS time approach
    	// To device blocking.)
    	//else
        {
    	
	        int progArr[] = { 0, 0, 0, 0,   //SET r0=0
	                          0, 0, 0, 0,   //SET r0=0 (repeated instruction to account for vagaries in student implementation of the CPU class)
	                         10, 0, 0, 0,   //PUSH r0
	                         15, 0, 0, 0 }; //TRAP
	
	        // Initialize the starting position for this program
	        // Force the alloc size to be a multiple of the page size
	        int add_space = 0;
	        int pageSize = m_MMU.getPageSize();
	        if(IDLE_PROCESS_LENGTH % pageSize != 0)
	        {
	        	add_space = pageSize - (IDLE_PROCESS_LENGTH % pageSize);
	        }
	        int size = IDLE_PROCESS_LENGTH + add_space;
	        int baseAddr = this.allocBlock(size); 
	        
	        if(ALLOC_ERROR == baseAddr)
	        {
	        	System.out.println("FATAL: Could not allocate memory for the IDLE PROCESS.");
	        	System.exit(0);
	        }
	        //Load the program into RAM
	        for(int i = 0; i < progArr.length; i++)
	        {
	            m_MMU.write(baseAddr + i, progArr[i]);
	        }
	
	
	        //Save the register info from the current process (if there is one)
	        if (m_currProcess != null)
	        {
	            m_currProcess.save(m_CPU);
	        }
	        
	        //Set the appropriate registers
	        m_CPU.setPC(0); // Changed to logical address
	        m_CPU.setSP(0); // NOTE: This is different from Nux's implementation (CPU-specific)
	        m_CPU.setBASE(baseAddr);
	        m_CPU.setLIM(baseAddr + size);

	        //Save the relevant info as a new entry in m_processes
	        m_currProcess = new ProcessControlBlock(IDLE_PROCESS_ID);  
	        m_processes.add(m_currProcess);
	        
	        // Save the registers.
	        // This is necessary for some of Nux's process-control code.
	        m_currProcess.save(m_CPU);
	        
	        // Debug statement removed per HW6 Specifications.
	        debugPrintln("Initialized the idle process (" + IDLE_PROCESS_ID +").");
        }
    }//createIdleProcess
    
    /*======================================================================
     * Program Management Methods
     *----------------------------------------------------------------------
     */

    /**
     * Create a new process with the specified amount of memory
     * @param prog The program to create
     * @param allocSize How much space should be allocated for the program
     */
    public void createProcess(Program prog, int allocSize)
    {
    	int pageSize = m_MMU.getPageSize();
    	
    	// Additional space that every program should have.
    	// If allocSize is not a multiple of the page size, then
    	// this value will be greater than zero.
    	int additional_space = 0;
    	if(allocSize % pageSize != 0)
    	{
    		additional_space = pageSize - (allocSize % pageSize);
    	}
    	
    	// Set the base register with a call to the mem_block allocator
    	int base = this.allocBlock(allocSize + additional_space);
    	
    	if(ALLOC_ERROR == base)
    	{
    		System.out.println("WARNING: Failed to allocate memory for a new process.");
    		return;
    	}
    	//this.m_CPU.setPC(this.m_CPU.getPC() - CPU.INSTRSIZE);
    	
    	// If there is a process currently running, save it.
    	if (null != this.m_currProcess)
    	{
    		m_currProcess.save(m_CPU);
    	}
    	// Build the ProcessControlBlock for this process.
    	ProcessControlBlock pcb = new ProcessControlBlock(m_nextProcessID++);
    	m_processes.add(pcb);
    	m_currProcess = pcb;
    	
    	// Initialize the process on the CPU.
    	this.m_CPU.setBASE(base);
    	this.m_CPU.setLIM(base + allocSize + additional_space);
    	
    	// Initialize the Stack and Program Counters to 0 (relative address).
    	this.m_CPU.setSP(0);
    	this.m_CPU.setPC(0);
    	
    	// "assemble" the program into an array of ints
    	int[] program = prog.export();
    	
    	for(int i = 0; i < program.length; ++i)
    	{
    		this.m_MMU.write(i + base, program[i]);
    	}
    
    	// Save the initial values.
    	m_currProcess.save(m_CPU);
    	
    	// Display the current state of RAM
    	this.printMemAlloc();
    	
    	// Debug statement removed per HW6 Specifications.
    	// debugPrintln("Process " + m_currProcess.processId + " Was initialized and loaded.");
    	
    }//createProcess
        
    /*======================================================================
     * Interrupt Handlers
     *----------------------------------------------------------------------
     */
    
    /**
     * An interrupt handler for illegal memory access.
     * Ends the program.
     */
    public void interruptIllegalMemoryAccess(int addr)
    {
    	System.out.println("Error: Illegal Memory Access attempted at address " + addr);
    	debugPrintln("Process " + this.m_currProcess.processId);
    	if(SOS.m_verbose)
    	{
    		this.m_CPU.regDump();
    	}
    	//this.printProcessTable();
    	System.exit(0);
    }
    
    /**
     * Interrupt handler for divide by zero errors.
     * Ends the program.
     */
    public void interruptDivideByZero()
    {
    	System.out.println("Error: Division by zero attempted.");
    	System.exit(0);    	
    }
    
    /**
     * Interrupt handler for when the clock's run a certain number of cycles.
     */
    public void interruptClock()
    {
    	// For now, schedule a new process.
    	scheduleNewProcess();
    }
    
    /**
     * Interrupt handler for illegal instructions.
     * Ends the program.
     */
    public void interruptIllegalInstruction(int[] instr)
    {
    	System.out.println("Error: Illegal instruction attempted: ");
    	System.out.println("Process: " + m_currProcess);
    	//this.printProcessTable();
    	m_CPU.printInstr(instr);
    	
    	System.exit(0);
    }
    
    public void interruptIOReadComplete(int devID, int addr, int data)
    {
    	// Verify that we know the device we're talking to.
    	DeviceInfo deviceInfo = this.getDeviceByID(devID);
    	if (null == deviceInfo)
    	{
    		// This should never happen.
    		// (To get here would require malicious work).
    		System.out.println("ERROR: Unrecognized device ID (" + devID + "). Ignoring the READ request.");
    		System.exit(0);
    	}
    	
    	// Verify that a process asked for this data
    	ProcessControlBlock pcb = this.selectBlockedProcess(deviceInfo.device, SOS.SYSCALL_READ, addr);
    	if (null == pcb)
    	{
    		// TODO Future homework: Handle the case where the process 
    		// that has requested some data has been killed off in the meantime.
    		// Maybe send flowers to the family?
    		
    		// In the meantime: Can't find a process to give the data to.
    		// PANIC!
    		System.out.println("ERROR: Could not find a process waiting to read. Ignoring the request.");
    		return;
    	}


    	// Otherwise, we have a valid process, and some data to push.
    	this.pushValueAndSuccessCode(pcb, data);
    	
    	pcb.unblock();
    }
    
    public void interruptIOWriteComplete(int devID, int addr)
    {
    	// Verify that SOS knows the device it is writing to.
    	DeviceInfo deviceInfo = this.getDeviceByID(devID);
    	if (null == deviceInfo)
    	{
    		// This should never happen.
    		// (To get here would require malicious work).
    		System.out.println("ERROR: Unrecognized device ID (" + devID + "). Unable to handle the WRITE request.");
    		System.exit(0);
    	}
    	
    	// Find the process that asked for the write to happen.
    	ProcessControlBlock pcb = this.selectBlockedProcess(deviceInfo.device, SOS.SYSCALL_WRITE, addr);
    	if (null == pcb)
    	{
    		// TODO Future homework: Handle the case where the process 
    		// that has written some data has gone missing.
    		// In the meantime, file a missing persons report.
    		
    		// In the meantime: Can't find a process to give the data to.
    		// PANIC!
    		System.out.println("ERROR: Could not find a process waiting for the WRITE.");
    		return;
    	}
    	// Otherwise, we have a valid process whose write operation has finished.
    	pcb.unblock();
    	this.push(pcb, SOS.SYSCALL_SUCCESS);
    }
    
    /*======================================================================
     * System Calls
     *----------------------------------------------------------------------
     */
    
    
    /**
	 * pushValueAndSuccessCode()
	 * 
	 * Temporarily restore the process that needs to push
	 * something to its stack, push the value to the stack,
	 * push a success code to the stack,
	 * then restore the process that was previously running.
	 * 
	 * This has the benefit of avoiding a large amount of copy-pasted code.
	 * To accomplish this, the implementation requires a constant-time
	 * O(4 * NUM_REGS) efficiency tradeoff.
	 * 
	 * @param pcb the Process we want to push something to
	 * @param value the value to push
	 */
	private void pushValueAndSuccessCode(ProcessControlBlock pcb, int value)
	{
		// If the process is currently running, no preservation is necessary.
		// In this case, the O(4 * NUM_REGS) penalty does not apply.
		if(pcb == m_currProcess)
		{
			m_CPU.push(value);
			m_CPU.push(SYSCALL_SUCCESS);
		}
		else
		{
	    	// Save the current process' register values
	    	ProcessControlBlock initialProcess = this.m_currProcess;
	    	initialProcess.save(m_CPU);
	    	
	    	// Push the value to the desired process
	    	pcb.restore(m_CPU);
	    	m_CPU.push(value);
	    	m_CPU.push(SYSCALL_SUCCESS);
	    	pcb.save(m_CPU);
	    	
	    	// Restore the original process to the CPU
	    	initialProcess.restore(m_CPU);
		}
		
	}

	/**
	 * push()
	 * 
	 * Temporarily restore the process that needs to push
	 * something to its stack, push the value to the stack,
	 * then restore the process that was previously running.
	 * 
	 * This has the benefit of avoiding a large amount of copy-pasted code.
	 * To accomplish this, the implementation requires a constant-time
	 * O(4 * NUM_REGS) efficiency tradeoff.
	 * 
	 * @param pcb the Process we want to push something to
	 * @param value the value to push
	 */
	private void push(ProcessControlBlock pcb, int value)
	{	
		// If the process is currently running, no preservation is necessary.
		// In this case, the O(4 * NUM_REGS) penalty does not apply.
		if(pcb == m_currProcess)
		{
			m_CPU.push(value);
		}
		else
		{
			if(SOS.isValidMemoryAccess(pcb, pcb.registers[CPU.LIM] - pcb.registers[CPU.SP] - 1))
			{
				pcb.registers[CPU.SP] += 1;
				this.m_MMU.write(pcb.registers[CPU.LIM] - pcb.registers[CPU.SP], value);
			}
		}
	}
	
	private static boolean isValidMemoryAccess(ProcessControlBlock pcb, int physicalAddress){
		return (physicalAddress >= pcb.registers[CPU.BASE] &&
				physicalAddress < pcb.registers[CPU.LIM]);
		
	}
	

	/**
     * Method for handling System Calls.
     * Parameters are passed via the calling program's stack.
     * See SOS documentation for more details.
     */
    public void systemCall()
    {
    	int s = m_CPU.pop();
        switch(s)
        {
        case SYSCALL_EXIT:
        	syscallExit();
        	break;
        case SYSCALL_GETPID:
        	syscallGetPID();
        	break;
        case SYSCALL_OUTPUT:
        	syscallOutput();
        	break;
        case SYSCALL_OPEN:
        	syscallOpen();
        	break;
        case SYSCALL_CLOSE:
        	syscallClose();
        	break;
        case SYSCALL_READ:
        	syscallRead();
        	break;
        case SYSCALL_WRITE:
        	syscallWrite();
        	break;
        case SYSCALL_EXEC:
        	syscallExec();
        	break;
        case SYSCALL_YIELD:
        	syscallYield();
        	break;
        case SYSCALL_COREDUMP:
        	syscallCoreDump();
        	break;
        }
    }
    
    /**
     * Kills the process.
     */
    private void syscallExit()
    {
    	this.removeCurrentProcess();
    	// Debug statement removed per HW6 Specifications.
    	// debugPrintln("Process " + m_currProcess.processId + " was removed.");
    	scheduleNewProcess();
    }
    
    /**
     * Prints some output.
     * The output is the top element of the stack
     * (push it before the Syscall ID).
     */
    private void syscallOutput()
    {
    	System.out.println("OUTPUT: " + m_CPU.pop());
    }
    
    /**
     * Puts the PID on the requesting program's stack.
     */
    private void syscallGetPID()
    {
    	m_CPU.push(m_currProcess.processId);
    }
    
    /**
     * System call handler that opens the file requested on the stack.
     */
    private void syscallOpen()
    {
    	int pid = m_CPU.pop();
    	DeviceInfo device = getDeviceByID(pid);
    	// Check for errors opening the device
    	if (null == device)
    	{
    		m_CPU.push(SYSCALL_INVALID_DEVICE_ERROR);
    		return;
    	}
    	if(device.procs.size() > 0 && !device.device.isSharable())
    	{
    		device.addProcess(m_currProcess);
    		m_currProcess.block(m_CPU, device.device, SYSCALL_OPEN, 0);
    		m_CPU.push(SYSCALL_SUCCESS); 
    		// By the time the device gets the memo
    		// The Operation will have succeeded.
    		scheduleNewProcess();
    		return;
    		//m_CPU.push(SYSCALL_DEVICE_NOT_SHAREABLE_ERROR);
    	}
    	// Only add the process if it's not already added.
    	if(device.containsProcess(m_currProcess))
    	{
    		m_CPU.push(SOS.SYSCALL_DEVICE_ALREADY_OPEN_ERROR);
    		return;
    	}
    	else
    	{
    		device.addProcess(m_currProcess);
    	}
    	m_CPU.push(SYSCALL_SUCCESS);
    	return;
    	
    }
    
    /**
     * System call handler that closes the specified file.
     */
    private void syscallClose()
    {
    	int pid = m_CPU.pop();
    	DeviceInfo device = getDeviceByID(pid);
    	// Check for errors opening the device
    	if (null == device)
    	{
    		m_CPU.push(SYSCALL_INVALID_DEVICE_ERROR);
    		return;
    	}
    	if(device.containsProcess(m_currProcess))
    	{
    		device.removeProcess(m_currProcess);
    		// Verify that [some process] is blocked
    		ProcessControlBlock pcb = selectBlockedProcess(device.device, SYSCALL_OPEN, 0);
    		if(pcb != null)
    		{
        		// Unblock the process that asked first.
    			device.procs.elementAt(0).unblock();
    		}
    	}
    	else
    	{
    		m_CPU.push(SOS.SYSCALL_DEVICE_NOT_OPEN_ERROR);
    		return;
    	}
    	m_CPU.push(SYSCALL_SUCCESS);
    	return;
    }
    
    /**
     * System call handler that reads from a currently-open file.
     */
    private void syscallRead()
    {
    	int addr = m_CPU.pop();
    	int device_id = m_CPU.pop();
    	DeviceInfo device = getDeviceByID(device_id);
    	// Check if the device is valid
    	if (null == device)
    	{
    		m_CPU.push(SYSCALL_INVALID_DEVICE_ERROR);
    		return;
    	}
    	if(m_currProcess.isBlocked())
    	{
    		m_CPU.push(SYSCALL_PROCESS_BLOCKED_ERROR);
    		return;
    	}
    	if (!device.device.isReadable())
    	{
    		m_CPU.push(SYSCALL_DEVICE_NOT_READABLE_ERROR);
    		return;
    	}
    	if(!device.containsProcess(m_currProcess))
    	{
    		m_CPU.push(SYSCALL_DEVICE_NOT_OPEN_ERROR);
    		return;
    	}
    	if(!device.device.isAvailable())
    	{
    		// Push values back to the stack
    		this.push(m_currProcess, device_id);
    		this.push(m_currProcess, addr);
    		this.push(m_currProcess, SOS.SYSCALL_READ);
    		
    		// Decrement the PC
    		this.m_CPU.setPC(m_CPU.getPC() - CPU.INSTRSIZE);
    		
    		// Schedule a new process
    		this.scheduleNewProcess();
    		// Short-circuit
    		return;
    	}
    	
    	// Otherwise, ask the device to read for us while we play a game of ping-pong.
    	// NOTE: This returns garbage (Value is dealt with via an interrupt handler)
    	device.device.read(addr); 
    	this.m_currProcess.block(m_CPU, device.device, SYSCALL_READ, addr);
    	this.scheduleNewProcess();
    	
    }
    
    /**
     * System call handler that writes the top item on the stack to the specified address.
     */
    private void syscallWrite()
    {
    	int value = m_CPU.pop();
    	int addr = m_CPU.pop();
    	int device_id = m_CPU.pop();
    	DeviceInfo device = getDeviceByID(device_id);
    	// Check if the device is valid
    	if (null == device)
    	{
    		m_CPU.push(SYSCALL_INVALID_DEVICE_ERROR);
    		return;
    	}
    	
    	if (!device.device.isWriteable())
    	{
    		m_CPU.push(SYSCALL_DEVICE_NOT_WRITEABLE_ERROR);
    		return;
    	}
    	if(!device.containsProcess(m_currProcess))
    	{
    		m_CPU.push(SYSCALL_DEVICE_NOT_OPEN_ERROR);
    		return;
    	}
    	if(!device.device.isAvailable())
    	{
    		// If the device is not available,
    		// Push the values back to the stack.
    		push(m_currProcess,device_id);
    		push(m_currProcess,addr);
    		push(m_currProcess,value);
    		push(m_currProcess,SOS.SYSCALL_WRITE);
    		
    		// Decrement the PC
    		this.m_CPU.setPC(m_CPU.getPC() - CPU.INSTRSIZE);
    		
    		// Schedule a new process
    		this.scheduleNewProcess();
    		// Short-circuit
    		return;
    	}
    	
    	// Otherwise, write.
    	device.device.write(addr, value);
    	m_currProcess.block(m_CPU, device.device, SOS.SYSCALL_WRITE, addr);
    	this.scheduleNewProcess();
    }
    
    /**
     * Prints a core dump and exits the program.
     */
    private void syscallCoreDump()
    {
    	System.out.println("==============================================================");
    	System.out.println("CORE DUMP!");
    	m_CPU.regDump();
    	
    	if(0 >= m_CPU.getSP())
    	{
    		System.out.println("The stack is empty.");
    	}
    	else
    	{
	    	// Print the contents of the stack.
	    	while(0 < m_CPU.getSP())
	    	{
	    		System.out.println("STACK[" + m_CPU.getSP() + "]: " + m_CPU.pop());
	    	}
	    	System.out.println("==============================================================");
    	}
    	syscallExit();
    	
    }
    
    /**
     * syscallExec
     *
     * creates a new process.  The program used to create that process is chosen
     * semi-randomly from all the programs that have been registered with the OS
     * via {@link #addProgram}.  Limits are put into place to ensure that each
     * process is run an equal number of times.  If no programs have been
     * registered then the simulation is aborted with a fatal error.
     *
     */
    private void syscallExec()
    {
        //If there is nothing to run, abort.  This should never happen.
        if (m_programs.size() == 0)
        {
            System.err.println("ERROR!  syscallExec has no programs to run.");
            System.exit(-1);
        }
        
        //find out which program has been called the least and record how many
        //times it has been called
        int leastCallCount = m_programs.get(0).callCount;
        for(Program prog : m_programs)
        {
            if (prog.callCount < leastCallCount)
            {
                leastCallCount = prog.callCount;
            }
        }

        //Create a vector of all programs that have been called the least number
        //of times
        Vector<Program> cands = new Vector<Program>();
        for(Program prog : m_programs)
        {
            cands.add(prog);
        }
        
        //Select a random program from the candidates list
        Random rand = new Random();
        int pn = rand.nextInt(m_programs.size());
        Program prog = cands.get(pn);

        //Determine the address space size using the default if available.
        //Otherwise, use a multiple of the program size.
        int allocSize = prog.getDefaultAllocSize();
        if (allocSize <= 0)
        {
            allocSize = prog.getSize() * 2;
        }

        //Load the program into RAM
        this.m_CPU.push(SYSCALL_SUCCESS);
        
        createProcess(prog, allocSize);
        

    }//syscallExec


    
    /**
     * Handle the YIELD system call: schedule a new process.
     * 
     */
    private void syscallYield()
    {
    	//m_currProcess.setCounter(m_currProcess.getCounter() - 1);
        this.scheduleNewProcess();
    }//syscallYield
    
    /**
     * Helper method for the Linux Scheduling Algorithm that updates a process' quantum.
     * 
     * @return true if the process can continue; false if the quantum is used up.
     */
    public boolean updateProcessTimes()
    {
    	this.m_currProcess.setCounter(this.m_currProcess.getCounter() - 1);
    	if(0 == this.m_currProcess.getCounter())
    	{
    		// The current process has used its Time Quantum. 
    		// We need to schedule a new process.
    		return false;
    	}
    	return true;
    }
    
    
  //======================================================================
    // Inner Classes
    //----------------------------------------------------------------------

    /**
     * class MemBlock
     *
     * This class contains relevant info about a memory block in RAM.
     *
     */
    private class MemBlock implements Comparable<MemBlock>
    {
        /** the address of the block */
        private int m_addr;
        /** the size of the block */
        private int m_size;

        /**
         * ctor does nothing special
         */
        public MemBlock(int addr, int size)
        {
            m_addr = addr;
            m_size = size;
        }

        /** accessor methods */
        public int getAddr() { return m_addr; }
        public int getSize() { return m_size; }
        
        /** setter methods */
        public void setAddr(int newVal) { m_addr = newVal; }
        public void setSize(int val) {m_size = val; }
        
        /**
         * compareTo              
         *
         * compares this to another MemBlock object based on address
         */
        public int compareTo(MemBlock m)
        {
            return this.m_addr - m.m_addr;
        }

    }//class MemBlock
    
    /**
     * class ProcessControlBlock
     *
     * This class contains information about a currently active process.
     */
    private class ProcessControlBlock implements
    	Comparable<ProcessControlBlock>
    {
        /**
         * a unique id for this process
         */
        private int processId = 0;
        
        /**
         * The priority of this process
         */
        private int priority = 0;
        
        /**
         * How many ticks left in a process's time quantum
         */
        
        private int counter = 0;

        /**
         * constructor
         *
         * @param pid        a process id for the process.  The caller is
         *                   responsible for making sure it is unique.
         */
        public ProcessControlBlock(int pid)
        {
            this.processId = pid;
            priority = SOS.BASE_PRIORITY;
            counter = CPU.CLOCK_FREQ;
        }

        /**
         * @return the current process' id
         */
        public int getProcessId()
        {
            return this.processId;
        }
        
        /**
         * These are the process' current registers.  If the process is in the
         * "running" state then these are out of date
         */
        private int[] registers = null;

        /**
         * If this process is blocked a reference to the Device is stored here
         */
        private Device blockedForDevice = null;
        
        /**
         * If this process is blocked a reference to the type of I/O operation
         * is stored here (use the SYSCALL constants defined in SOS)
         */
        private int blockedForOperation = -1;
        
        /**
         * If this process is blocked reading from a device, the requested
         * address is stored here.
         */
        private int blockedForAddr = -1;
        
        
        /**
         * the time it takes to load and save registers, specified as a number
         * of CPU ticks
         */
        private static final int SAVE_LOAD_TIME = 30;
        
        /**
         * Used to store the system time when a process is moved to the Ready
         * state.
         */
        private int lastReadyTime = -1;
        
        /**
         * Used to store the number of times this process has been in the ready
         * state
         */
        private int numReady = 0;
        
        /**
         * Used to store the maximum starve time experienced by this process
         */
        private int maxStarve = -1;
        
        /**
         * Used to store the average starve time for this process
         */
        private double avgStarve = 0;
        
        
        /**
         * block
         *
         * blocks the current process to wait for I/O.  The caller is
         * responsible for calling {@link CPU#scheduleNewProcess}
         * after calling this method.
         *
         * @param cpu   the CPU that the process is running on
         * @param dev   the Device that the process must wait for
         * @param op    the operation that the process is performing on the
         *              device.  Use the SYSCALL constants for this value.
         * @param addr  the address the process is reading from (for SYSCALL_READ)
         * 
         */
        public void block(CPU cpu, Device dev, int op, int addr)
        {
        	// Debug statement removed per HW6 Specifications.
        	// debugPrintln("Blocking process " + this.processId + ".");
            blockedForDevice = dev;
            blockedForOperation = op;
            blockedForAddr = addr;
            
        }//block
        
        /**
         * save
         *
         * saves the current CPU registers into this.registers
         *
         * @param cpu  the CPU object to save the values from
         */
        public void save(CPU cpu)
        {
            //A context switch is expensive.  We simluate that here by 
            //adding ticks to m_CPU
            m_CPU.addTicks(SAVE_LOAD_TIME);
            
            //Save the registers
            int[] regs = cpu.getRegisters();
            this.registers = new int[CPU.NUMREG];
            for(int i = 0; i < CPU.NUMREG; i++)
            {
                this.registers[i] = regs[i];
            }

            //Assuming this method is being called because the process is moving
            //out of the Running state, record the current system time for
            //calculating starve times for this process.  If this method is
            //being called for a Block, we'll adjust lastReadyTime in the
            //unblock method.
            numReady++;
            lastReadyTime = m_CPU.getTicks();
            
        }//save
         
        /**
         * restore
         *
         * restores the saved values in this.registers to the current CPU's
         * registers
         *
         * @param cpu  the CPU object to restore the values to
         */
        public void restore(CPU cpu)
        {
            //A context switch is expensive.  We simluate that here by 
            //adding ticks to m_CPU
            m_CPU.addTicks(SAVE_LOAD_TIME);
            
            //Restore the register values
            int[] regs = cpu.getRegisters();
            for(int i = 0; i < CPU.NUMREG; i++)
            {
                regs[i] = this.registers[i];
            }

            //Record the starve time statistics
            int starveTime = m_CPU.getTicks() - lastReadyTime;
            if (starveTime > maxStarve)
            {
                maxStarve = starveTime;
            }
            double d_numReady = (double)numReady;
            avgStarve = avgStarve * (d_numReady - 1.0) / d_numReady;
            avgStarve = avgStarve + (starveTime * (1.0 / d_numReady));
        }//restore
         
        /**
         * unblock
         *
         * moves this process from the Blocked (waiting) state to the Ready
         * state. 
         *
         */
        public void unblock()
        {
            //Reset the info about the block
            blockedForDevice = null;
            blockedForOperation = -1;
            blockedForAddr = -1;
            
            //Assuming this method is being called because the process is moving
            //from the Blocked state to the Ready state, record the current
            //system time for calculating starve times for this process.
            lastReadyTime = m_CPU.getTicks();
            
        }//unblock
        
        /**
         * isBlocked
         *
         * @return true if the process is blocked
         */
        public boolean isBlocked()
        {
            return (blockedForDevice != null);
        }//isBlocked
         
        /**
         * isBlockedForDevice
         *
         * Checks to see if the process is blocked for the given device,
         * operation and address.  If the operation is an open, the given
         * address is ignored.
         *
         * @param dev   check to see if the process is waiting for this device
         * @param op    check to see if the process is waiting for this operation
         * @param addr  check to see if the process is reading from this address
         *
         * @return true if the process is blocked by the given parameters
         */
        public boolean isBlockedForDevice(Device dev, int op, int addr)
        {
            if ( (blockedForDevice == dev) && (blockedForOperation == op) )
            {
                if (op == SYSCALL_OPEN)
                {
                    return true;
                }

                if (addr == blockedForAddr)
                {
                    return true;
                }
            }//if

            return false;
        }//isBlockedForDevice
         
        /**
         * getRegisterValue
         *
         * Retrieves the value of a process' register that is stored in this
         * object (this.registers).
         * 
         * @param idx the index of the register to retrieve.  Use the constants
         *            in the CPU class
         * @return one of the register values stored in in this object or -999
         *         if an invalid index is given 
         */
        public int getRegisterValue(int idx)
        {
            if ((idx < 0) || (idx >= CPU.NUMREG))
            {
                return -999;    // invalid index
            }
            
            return this.registers[idx];
        }//getRegisterValue
         
        /**
         * setRegisterValue
         *
         * Sets the value of a process' register that is stored in this
         * object (this.registers).  
         * 
         * @param idx the index of the register to set.  Use the constants
         *            in the CPU class.  If an invalid index is given, this
         *            method does nothing.
         * @param val the value to set the register to
         */
        public void setRegisterValue(int idx, int val)
        {
            if ((idx < 0) || (idx >= CPU.NUMREG))
            {
                return;    // invalid index
            }
            
            this.registers[idx] = val;
        }//setRegisterValue
        
        /**
         * move(): This method moves a process to a new location in RAM and shifts
         * its register values to reflect this movement. If the process being moved
         * is the currently running process, then the CPU's registers will need to be
         * shifted as well to reflect the move.
         * 
         * @param newBase The new location in RAM where the new process will be moved.
         * @return Whether the move was successful.
         */
        public boolean move(int newBase)
        {
        	// Retrieve the relevant old location values 
        	int oldBase = this.getRegisterValue(CPU.BASE);
        	int oldLim = this.getRegisterValue(CPU.LIM);
        	int size = oldLim - oldBase;
        	
        	//Fail if there's not enough room in the RAM to move a process
        	if(newBase < m_firstLoadPos || newBase >= m_MMU.getSize())
        	{
        		return false;
        	}
        	if((newBase + size) > m_MMU.getSize())
        	{
        		return false;
        	}
        	
        	//Adjust the CPU's registers if the process we are moving
        	//is currently in the RUNNING state
        	if(0 == this.compareTo(m_currProcess))
        	{
        		oldBase = m_CPU.getBASE();
        		oldLim = m_CPU.getLIM();
        		
        		m_CPU.setBASE(newBase);
        		m_CPU.setLIM(newBase + size);
        	}
        	
        	//Move the register values
        	oldBase = this.getRegisterValue(CPU.BASE); 
        	this.setRegisterValue(CPU.BASE, newBase);
        	this.setRegisterValue(CPU.LIM, newBase + size);
        	
        	// Adjust the values in the page table to reflect the move
        	// 1. Calculate the number of pages for the process's address space
        	// 2. Determine the old page table locations.
        	// 3. Determine the new page table locations.
        	// 4. Copy the values from the old page table locations to
        	//		the new page table locations. 
        	int numPages = size/(m_MMU.getPageSize());
        	int pageMask = m_MMU.getPageMask();
        	int offsetSize = m_MMU.getOffsetSize();
        	
        	int oldFirstPage = (oldBase & pageMask) >> offsetSize;
        	int newFirstPage = (newBase & pageMask) >> offsetSize;
	    	
	    	//System.out.println("======= (Process: " + processId + ")=========");
	    	//System.out.println("Old First Page: " + oldFirstPage);
	    	//System.out.println("New First Page: " + newFirstPage);
	    	
	    	for(int i = 0; i < numPages; ++i)
	    	{
	    		int frameNum = m_RAM.read(oldFirstPage + i);
	    		int oldData = m_RAM.read(newFirstPage + i);
	    		m_RAM.write(oldFirstPage + i, oldData);
	    		m_RAM.write(newFirstPage + i, frameNum);
	    	}
	    	
	    	//printPageTable();
        	
        	debugPrintln("Process " + this.processId + " has moved from " + oldBase + " to " + newBase);
        	
			return true;
            
        }//move
    
        /**
         * @return the last time this process was put in the Ready state
         */
        public long getLastReadyTime()
        {
            return lastReadyTime;
        }
        
        
        /**
         * overallAvgStarve
         *
         * @return the overall average starve time for all currently running
         *         processes
         *
         */
        public double overallAvgStarve()
        {
            double result = 0.0;
            int count = 0;
            for(ProcessControlBlock pi : m_processes)
            {
                if (pi.avgStarve > 0)
                {
                    result = result + pi.avgStarve;
                    count++;
                }
            }
            if (count > 0)
            {
                result = result / count;
            }
            
            return result;
        }//overallAvgStarve
         
        /**
         * toString       **DEBUGGING**
         *
         * @return a string representation of this class
         */
        public String toString()
        {
            //Print the Process ID and process state (READY, RUNNING, BLOCKED)
            String result = "Process id " + processId + " ";
            if (isBlocked())
            {
                result = result + "is BLOCKED for ";
                //Print device, syscall and address that caused the BLOCKED state
                if (blockedForOperation == SYSCALL_OPEN)
                {
                    result = result + "OPEN";
                }
                else
                {
                    result = result + "WRITE @" + blockedForAddr;
                }
                for(DeviceInfo di : m_devices)
                {
                    if (di.getDevice() == blockedForDevice)
                    {
                        result = result + " on device #" + di.getId();
                        break;
                    }
                }
                result = result + ": ";
            }
            else if (this == m_currProcess)
            {
                result = result + "is RUNNING: ";
            }
            else
            {
                result = result + "is READY: ";
            }

            //Print the register values stored in this object.  These don't
            //necessarily match what's on the CPU for a Running process.
            if (registers == null)
            {
                result = result + "<never saved>";
                return result;
            }
            
            for(int i = 0; i < CPU.NUMGENREG; i++)
            {
                result = result + ("r" + i + "=" + registers[i] + " ");
            }//for
            result = result + ("PC=" + registers[CPU.PC] + " ");
            result = result + ("SP=" + registers[CPU.SP] + " ");
            result = result + ("BASE=" + registers[CPU.BASE] + " ");
            result = result + ("LIM=" + registers[CPU.LIM] + " ");

            //Print the starve time statistics for this process
            result = result + "\n\t\t\t";
            result = result + " Max Starve Time: " + maxStarve;
            result = result + " Avg Starve Time: " + avgStarve;
        
            return result;
        }//toString
        
        /**
         * compareTo              
         *
         * compares this to another ProcessControlBlock object based on the BASE addr
         * register.  Read about Java's Collections class for info on
         * how this method can be quite useful to you.
         */
        public int compareTo(ProcessControlBlock pi)
        {
            return this.registers[CPU.BASE] - pi.registers[CPU.BASE];
        }
        
        /**
         * This gets the 'goodness' of a process.
         * Used in the Linux Scheduling Algorithm.
         * @return the 'goodness' value.
         */
        public int goodness()
        {
        	if(0 == this.counter)
        	{
        		return 0;
        	}
        	return (this.counter + this.priority);
        }
        
        /**
         * This gets a process' priority in the Linux Scheduling Algorithm.
         * @return the priority of the process.
         */
        public int getPriority()
        {
        	return this.priority;
        }
        
        /**
         * Function for seting the priority of the process.
         * Used for the Linux sheduling algorithm.
         * @param prior the priority to set
         */
        public void setPriority(int prior)
        {
        	this.priority = prior;
        }
        
        /**
         * Function for setting the counter of how long a program has been running
         * this Epoch. Used for the Linux Scheduling Algorithm.
         * @param count updated quantum remaining
         */
        public void setCounter(int count)
        {
        	this.counter = count;
        }
        
        /**
         * Function to get the remaining quantum for this epoch.
         * Used in the Linux Scheduling Algorithm.
         * @return Quantum remaining.
         */
        public int getCounter()
        {
        	return this.counter;
        }
        
    }//class ProcessControlBlock
    
    
    
    

    /**
     * A comparator for ProcessControlBlocks.
     * At present, unused. Intended to be used in a heap in the Linux Scheduling Algorithm.
     * Compares two PCBs by goodness and returns an inverted subtraction between the two.
     * @author Dan Ehmig
     *
     */
    private class PCBCompare implements Comparator<ProcessControlBlock>
    {
    	/**
    	 * Custom compare method that compares two process control blocks.
    	 * Returns 0 if the two processes have equal priority. Returns a positive
    	 * integer if the first process has a LOWER priority. Returns a negative
    	 * integer if the first process has a HIGHER priority than the first. We
    	 * switch the typical return values because we want a MAX heap.
    	 */
		@Override
		public int compare(ProcessControlBlock pcb1, ProcessControlBlock pcb2) {
			int good1 = pcb1.goodness();
			int good2 = pcb2.goodness();
			
			if(good1 < good2)
			{
				return 1;
			}
			else if(good1 > good2)
			{
				return -1;
			}
			else
			{
				return 0;
			}
		}
    	
    	
    }
    
    
    /**
     * class DeviceInfo
     *
     * This class contains information about a device that is currently
     * registered with the system.
     */
    private class DeviceInfo
    {
        /** every device has a unique id */
        private int id;
        /** a reference to the device driver for this device */
        private Device device;
        /** a list of processes that have opened this device */
        private Vector<ProcessControlBlock> procs;

        /**
         * constructor
         *
         * @param d          a reference to the device driver for this device
         * @param initID     the id for this device.  The caller is responsible
         *                   for guaranteeing that this is a unique id.
         */
        public DeviceInfo(Device d, int initID)
        {
            this.id = initID;
            this.device = d;
            d.setId(initID);
            this.procs = new Vector<ProcessControlBlock>();
        }

        /** @return the device's id */
        public int getId()
        {
            return this.id;
        }

        /** @return this device's driver */
        public Device getDevice()
        {
            return this.device;
        }

        /** Register a new process as having opened this device */
        public void addProcess(ProcessControlBlock pi)
        {
            procs.add(pi);
        }
        
        /** Register a process as having closed this device */
        public void removeProcess(ProcessControlBlock pi)
        {
            procs.remove(pi);
        }

        /** Does the given process currently have this device opened? */
        public boolean containsProcess(ProcessControlBlock pi)
        {
            return procs.contains(pi);
        }
        
        /** Is this device currently not opened by any process? */
        public boolean unused()
        {
            return procs.size() == 0;
        }
        
    }//class DeviceInfo

};//class SOS
