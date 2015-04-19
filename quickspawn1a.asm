####################################################
# This program simply spawns another process and
# then exits
###################################################

#spawn a new process
SET r4 7       #EXEC sys call id
PUSH r4        #push the sys call id onto the stack
TRAP           #make the system call

#exit syscall
:exit
SET  r4 0      #EXIT system call id
PUSH r4        #push sys call id on stack
TRAP           #exit the program


