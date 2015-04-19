####################################################
#This CPU-bound program spawns a new process and
#then runs for a while
###################################################

#spawn a new process
SET r4 7       #EXEC sys call id
PUSH r4        #push the sys call id onto the stack
TRAP           #make the system call

#Initialize the loop variables
SET r1 0       #counter
SET r2 1       #increment amount
SET r3 10      #limit

#Main Loop
:cpuloop
ADD r1 r2 r1

#Save the loop variables
PUSH r1
PUSH r2
PUSH r3

#Do some random calculations
SET r0 7531
SET r4 2468
DIV r0 r0 r4 
ADD r0 r0 r4
SET r2 2
PUSH r0
MUL r0 r2 r0
PUSH r0
SET r1 54321
PUSH r1
SET r0 90909
POP r3
POP r0
COPY r4 r0
SUB r1 r0 r4
SET r2 1       
SET r3 3       
POP r0

#Restore the main loop variables
POP r3
POP r2
POP r1

#loop test
BNE r1 r3 cpuloop

#========================================
#This is a normal exit from the program
#========================================
:exit
SET  r4 0      #EXIT system call id
PUSH r4        #push sys call id on stack
TRAP           #exit the program
