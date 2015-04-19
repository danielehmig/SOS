####################################################
#This CPU-bound program simply runs for a while and exits
###################################################

#Initialize the loop variables
SET r1 0       #counter
SET r2 1       #increment amount
SET r3 4       #limit

#Main Loop
:cpuloop
ADD r1 r2 r1

#Save the loop variables
PUSH r1
PUSH r2
PUSH r3

#This program calculates C(R0,R1) as per
#discrete mathematics.  Initialize R0 = R1 + 3
SET R2 3             
ADD R0 r1 r2

#STEP 1: Calculate m! and put it onto the stack
#Setup some initial variables
SET R2 1       #increment amount
SET R3 0       #counter (this will count backwards)
ADD R3 R3 R0
SET R4 1       #result

#Calculate m!
:factm
MUL R4 R4 R3          #multiply the counter into R4
SUB R3 R3 R2          #decrement the counter
BLT R2 R3 factm       #repeat until R3==0

#Push the result
PUSH R4        #push the result 

#STEP 2: Calculate n! and put it onto the stack
#Setup some initial variables
SET R2 1       #increment amount
SET R3 0       #counter (this will count backwards)
ADD R3 R3 R1
SET R4 1       #result

#Calculate
:factn
MUL R4 R4 R3          #multiply the counter into R4
SUB R3 R3 R2          #decrement the counter
BLT R2 R3 factn       #repeat until R3==0

#Push the result
PUSH R4        #push the result 

#STEP 3: Calculate (m-n)! and put it onto the stack
#Setup some initial variables
SET R2 1       #increment amount
SUB R3 R0 R1   #counter (this will count backwards)
SET R4 1       #result

#Calculate
:factmn
MUL R4 R4 R3          #multiply the counter into R4
SUB R3 R3 R2          #decrement the counter
BLT R2 R3 factmn      #repeat until R3==0

#Push the result
PUSH R4        #push the result

#STEP 4: Calculate the and print the result using the
#        formula:  m! / ((n!)(m-n!))
#Pop off our values into registers
POP R4        #R4 = (m-n)!
POP R3        #R3 = n!
POP R2        #R2 = m!

#Calculate the result
MUL R1 R3 R4
SET R0 0
BNE R0 R1 dodiv
BRANCH skipdiv
:dodiv
DIV R1 R2 R1
:skipdiv

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
