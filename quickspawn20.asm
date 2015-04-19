####################################################
# This program simply spawns 20 other processes
###################################################

#Initialize the variables
SET r1 0       #counter
SET r2 1000    #increment amount
SET r3 20000   #limit

#begin loop
:loop
ADD r1 r2 r1

#spawn a new process
SET r4 7       #EXEC sys call id
PUSH r4        #push the sys call id onto the stack
TRAP           #make the system call

#end of loop
BNE r1 r3 loop #repeat 40 times

#exit syscall
:exit
SET  r4 0      #EXIT system call id
PUSH r4        #push sys call id on stack
TRAP           #exit the program


