#include <EEPROM.h>
#include <SoftwareSerial.h>

// incoming buffer size
#define BUFFER_SIZE           50

// FSM states
#define S_WAITING_CONNECTION  0
#define S_CONNECTED           1

// PINs
#define OUT                   9
#define OUTLED                7
#define BTLED                 8

// Bluetooth device's properties
const char* BT_DEVICE_NAME = "BTSwitch";
const char* BT_DEVICE_PIN = "1234";

byte actual_status;
byte out_status;
byte leds_status;

char incoming_buffer[BUFFER_SIZE];
char incoming_char;
byte buffer_position;

// create a new SoftwareSerial on pin 2 (RX) and 3 (TX)
// for WT11 connection
SoftwareSerial wt11Serial(2, 3);


void setup() {
  
  wt11Serial.begin(9600);
  Serial.begin(115200); 
 
  Serial.println("BTSwitch running...");
  Serial.println(); 


  pinMode(OUT, OUTPUT);
  pinMode(OUTLED, OUTPUT);    
  pinMode(BTLED, OUTPUT);  
  
  // restore output pin status 
  // reading it from internal EEPROM
  Serial.print("Restoring saved status: ");
  readOutStatus();
  printStatus(Serial);
  Serial.println();
  Serial.println();
  setOutputs();

  // init Bluegiga WT11
  initWT11();
  Serial.println("WT11 initialized with:");
  Serial.print("BT NAME:\t");
  Serial.println(BT_DEVICE_NAME);
  Serial.print("BT AUTH:\t");
  Serial.println(BT_DEVICE_PIN);
  Serial.println();
  
  // wait for incoming connection and reset receiving buffer
  actual_status = S_WAITING_CONNECTION;
  buffer_position = 0;
}

void loop() {
  
  // new data from WT11...
  if (wt11Serial.available() > 0) {

    // Get incoming char
    incoming_char = wt11Serial.read();
    
    // if new line (LF = 0x0A), terminate string and parse data
    // finally reset receiving buffer
    if(incoming_char == '\n') {
      incoming_buffer[buffer_position] = '\0';
      parseData();
      buffer_position = 0; 
    } 
    
    // if buffer is not full, save incoming char
    else if(buffer_position < BUFFER_SIZE - 2) {
      incoming_buffer[buffer_position] = incoming_char;
      buffer_position++;      
            
    // if buffer is full, restart from position 0
    } else {
      incoming_buffer[0] = incoming_char;
      buffer_position = 1;
    }
  }
}


void initWT11() {  
  
  // set WT11 properties (name, spp profile status, auth pin)
  wt11Serial.print("SET BT NAME ");
  wt11Serial.println(BT_DEVICE_NAME);
  wt11Serial.println("SET PROFILE SPP ON");
  wt11Serial.print("SET BT AUTH * ");
  wt11Serial.println(BT_DEVICE_PIN);
  wt11Serial.println("RESET");
  wt11Serial.println("SET");
  
  // consume garbage data
  while(wt11Serial.available() > 0) wt11Serial.read();
}


void parseData() {

  Serial.print("Incoming data -> ");
  Serial.println(incoming_buffer);
  
  switch(actual_status) {
    
    // waiting for new connetion...
    case S_WAITING_CONNECTION:
    
      // RING signal from WT11 = new connection established!
      if(strstr(incoming_buffer, "RING") != 0) {
        actual_status = S_CONNECTED;
        if(leds_status == 1) digitalWrite(BTLED, HIGH);
        Serial.println("Connected :)");
      }
      break;
    
    // connection in progress, parse incoming commands...
    case S_CONNECTED:
    
      // NO CARRIER signal from WT11 = connection lost!
      if(strstr(incoming_buffer, "NO CARRIER") != 0) {        
        actual_status = S_WAITING_CONNECTION;
        if(leds_status == 1) digitalWrite(BTLED, LOW);
        Serial.println("Disconnected :(");
      }

      // ? command, used by peer to verify if everything is ok
      else if(strcmp(incoming_buffer, "?") == 0)
        wt11Serial.println("!"); 
      
      // ABOUT command
      else if(strcmp(incoming_buffer, "ABOUT") == 0)
        wt11Serial.println("BTSwitch 1.0");   

      // STATUS command
      else if(strcmp(incoming_buffer, "STATUS") == 0) {
        printStatus(wt11Serial);
        wt11Serial.println();
      }

      // OUTPUT change commands
      
      else if(strcmp(incoming_buffer, "OUT_ON") == 0) {
        if(out_status != 1) {
          out_status = 1;
          digitalWrite(OUT, HIGH);
          if(leds_status == 1) digitalWrite(OUTLED, HIGH);
          EEPROM.write(1, out_status);
          wt11Serial.println("OK");
          Serial.println("OUT set HIGH");
        } else {
          wt11Serial.println("NOCHANGE");
          Serial.println("OUT already HIGH");
        }
      }
      
      else if(strcmp(incoming_buffer, "OUT_OFF") == 0) {
        if(out_status != 0) {
          out_status = 0;
          digitalWrite(OUT, LOW);
          if(leds_status == 1) digitalWrite(OUTLED, LOW);
          EEPROM.write(1, out_status);
          wt11Serial.println("OK");
          Serial.println("OUT set LOW");
        } else {
          wt11Serial.println("NOCHANGE");
          Serial.println("OUT already LOW");
        }
      }
      
      else if(strcmp(incoming_buffer, "LEDS_ON") == 0) {
        if(leds_status != 1) {
          leds_status = 1;
          digitalWrite(OUTLED, out_status);
          digitalWrite(BTLED, HIGH);
          EEPROM.write(2, leds_status);
          wt11Serial.println("OK");
          Serial.println("LEDs set ON");
        } else {
          wt11Serial.println("NOCHANGE");
          Serial.println("LEDs already ON");
        }
      }
      
      else if(strcmp(incoming_buffer, "LEDS_OFF") == 0) {
        if(leds_status != 0) {
          leds_status = 0;
          digitalWrite(OUTLED, LOW);          
          digitalWrite(BTLED, LOW);
          EEPROM.write(2, leds_status);
          wt11Serial.println("OK");
          Serial.println("LEDs set OFF");
        } else {
          wt11Serial.println("NOCHANGE");
          Serial.println("LEDs already OFF");
        }
      
    } else {
        wt11Serial.println("UNKNOWN");
        Serial.println("Unknown command");
      }
        
      break;
  }
}

// Read pins' status from EEPROM
// Every value different from 1 is considered 0
void readOutStatus() {
  
  out_status = EEPROM.read(1);
  if(out_status != 1) out_status = 0;
  
  leds_status = EEPROM.read(2);  
  if(leds_status != 1) leds_status = 0;
}

void setOutputs() {
  
  digitalWrite(OUT, out_status);
  if(leds_status == 1) digitalWrite(OUTLED, out_status);
}

// Print actual status in the form OUT_STATUS|LEDS_STATUS
void printStatus(Print &printObject) {

  if(out_status == 1) printObject.print("ON"); else printObject.print("OFF");
  printObject.print("|");
  if(leds_status == 1) printObject.print("ON"); else printObject.print("OFF");
}
