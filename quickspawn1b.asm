####################################################
# This program simply spawns another process and
# then exits.  Additional do-nothing code has been
# added to make it larger than other quickspawn1
# programs
###################################################

#spawn a new process
SET r4 7       #EXEC sys call id
PUSH r4        #push the sys call id onto the stack
TRAP           #make the system call

#do-nothing code
SET r0 1
SET r0 2
SET r0 3
SET r0 4
SET r0 5
SET r0 6
SET r0 7
SET r0 8
SET r0 9
SET r0 10

#exit syscall
:exit
SET  r4 0      #EXIT system call id
PUSH r4        #push sys call id on stack
TRAP           #exit the program


