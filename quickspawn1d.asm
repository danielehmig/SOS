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
SET r1 1
SET r1 2
SET r1 3
SET r1 4
SET r1 5
SET r1 6
SET r1 7
SET r1 8
SET r1 9
SET r1 10
SET r2 1
SET r2 2
SET r2 3
SET r2 4
SET r2 5
SET r2 6
SET r2 7
SET r2 8
SET r2 9
SET r2 10

#exit syscall
:exit
SET  r4 0      #EXIT system call id
PUSH r4        #push sys call id on stack
TRAP           #exit the program


