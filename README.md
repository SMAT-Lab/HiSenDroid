Hidden Sensitive Operation


## Setup
The following is required to set up HSO:
 MAC system

##### Step 1: Load dependencies to your local repository
* git clone https://se_anonymous@bitbucket.org/se_anonymous/hisendroid.git
* cd HiSenDroid
* ./res/loadDependencies.sh

##### Step 2: build package：
mvn clean install

##### Step 3: example of running HSO(3 parameters):
* Parameters are needed here: [your_apk_path.apk],[path of android.jar],[path of psCout.txt]
* example:
/yourpath/a0b2075ec2ace0b12489668d08d85c3a.apk
/yourpath/android-platforms/android-17/android.jar
/yourpath/HSO/res/psCout.txt

