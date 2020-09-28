run:
	java -cp .:org.json.jar sim.Simulator --teams g1 random g2 random g3 random g4 random g5 random -r 10 -s 10 -l log.txt -e games.csv

gui:
	java -cp .:org.json.jar sim.Simulator --teams g2 g5 g2 g5 g2 g5 g2 g5 g2 g5 -r 25 -s 234 -f 15 --gui -d -l log.txt

compile:
	javac -cp .:org.json.jar */*.java

clean:
	rm */*.class
