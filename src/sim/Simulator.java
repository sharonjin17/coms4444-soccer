/*
    Project: Retroactive Soccer
    Course: COMS 4444 Programming & Problem Solving (Fall 2020)
    Instructor: Prof. Kenneth Ross
    URL: http://www.cs.columbia.edu/~kar/4444f20
    Author: Aditya Sridhar
    Simulator Version: 1.0
*/

package sim;

import java.awt.Desktop;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Simulator {
	
	// Simulator structures
	private static GameHistory gameHistory;
	private static List<PlayerWrapper> playerWrappers;
	private static List<String> playerNames, playerNamesWithDuplicates;
	private static Integer[][] randomGameGrid;
	private static Random random;
	
	// Simulator inputs
	private static int seed = 10;
	private static int rounds = 10;
	private static double fpm = 15;
	private static boolean showGUI = false;
	private static boolean continuousGUI = true;
	private static boolean exportCSV = false;
	
	// Defaults
	private static boolean enablePrints = false;
	private static long timeout = 1000;
	private static int currentRound = 0;
	private static String version = "1.0";
	private static String projectPath, sourcePath, staticsPath, csvPath;
    

	private static void setup() {
		gameHistory = new GameHistory();
		random = new Random(seed);
		projectPath = new File(".").getAbsolutePath().substring(0, 
				new File(".").getAbsolutePath().indexOf("coms4444-soccer") + "coms4444-soccer".length());
		sourcePath = projectPath + File.separator + "src";
		staticsPath = projectPath + File.separator + "statics";
	}

	private static void parseCommandLineArguments(String[] args) {
		playerWrappers = new ArrayList<>();
		playerNames = new ArrayList<>();
		playerNamesWithDuplicates = new ArrayList<>();
		Map<String, Integer> playerNameMap = new HashMap<>();

		for(int i = 0; i < args.length; i++) {
            switch (args[i].charAt(0)) {
                case '-':
                    if(args[i].equals("-t") || args[i].equals("--teams")) {
                        while(i + 1 < args.length && args[i + 1].charAt(0) != '-') {
                            i++;
                            String playerName = args[i];
                            playerNamesWithDuplicates.add(playerName);
                            if(!playerNameMap.containsKey(playerName))
                            	playerNameMap.put(playerName, 0);
                            playerNameMap.put(playerName, playerNameMap.get(playerName) + 1);
                        }

                        if(playerNamesWithDuplicates.size() < 2)
                            throw new IllegalArgumentException("You entered an invalid number of teams. At least 2 teams are required for the league.");
                    } 
                    else if(args[i].equals("-g") || args[i].equals("--gui"))
                        showGUI = true;
                    else if(args[i].equals("-c") || args[i].equals("--continuous"))
                        continuousGUI = true;
                    else if(args[i].equals("-d") || args[i].equals("--discrete"))
                        continuousGUI = false;
                    else if(args[i].equals("-l") || args[i].equals("--log")) {
                        i++;
                    	if(i == args.length) 
                            throw new IllegalArgumentException("The log file path is missing!");
                        Log.setLogFile(args[i]);
                        Log.assignLoggingStatus(true);
                    }
                    else if(args[i].equals("-e") || args[i].equals("--export")) {
                        i++;
                    	if(i == args.length) 
                            throw new IllegalArgumentException("The CSV file path is missing!");
                    	exportCSV = true;
                    	csvPath = args[i];
                    }
                    else if(args[i].equals("-v") || args[i].equals("--verbose"))
                        Log.assignVerbosityStatus(true);
                    else if(args[i].equals("-f") || args[i].equals("--fpm")) {
                    	i++;
                        if(i == args.length) 
                            throw new IllegalArgumentException("The GUI frames per minute is missing!");
                        fpm = Double.parseDouble(args[i]);
                    }
                    else if(args[i].equals("-s") || args[i].equals("--seed")) {
                    	i++;
                        if(i == args.length) 
                            throw new IllegalArgumentException("The seed number is missing!");
                        seed = Integer.parseInt(args[i]);
                		random = new Random(seed);
                    }
                    else if(args[i].equals("-r") || args[i].equals("--rounds")) {
                    	i++;
                        if (i == args.length) 
                            throw new IllegalArgumentException("The total number of rounds is not specified!");
                        rounds = Integer.parseInt(args[i]);
                    }
                    else 
                        throw new IllegalArgumentException("Unknown argument \"" + args[i] + "\"!");
                    break;
                default:
                    throw new IllegalArgumentException("Unknown argument \"" + args[i] + "\"!");
            }
        }

		for(String name : playerNameMap.keySet()) {
			int numTeams = playerNameMap.get(name);
			if(numTeams == 1) {
				playerNames.add(name);
                try {
					playerWrappers.add(loadPlayerWrapper(cleanName(name), name));
				} catch (Exception e) {
					Log.writeToLogFile("Unable to load player: " + e.getMessage());
				}
			}
			else {
				Log.writeToLogFile(numTeams + " teams have the name \"" + name + "\"!");
				for(int i = 1; i <= numTeams; i++) {
					String newName = name + "_" + i;
					playerNames.add(newName);
                    try {
						playerWrappers.add(loadPlayerWrapper(cleanName(newName), newName));
					} catch (Exception e) {
						Log.writeToLogFile("Unable to load player: " + e.getMessage());
					}
					Log.writeToLogFile("Team \"" + name + "\" at index " + i + " is being renamed as \"" + newName + "\"!");
				}
			}			
		}
		
		Log.writeToLogFile("\n");
        Log.writeToLogFile("Project: Retroactive Soccer");
        Log.writeToLogFile("Simulator Version: " + version);
        Log.writeToLogFile("Players: " + playerNames.toString());
        Log.writeToLogFile("GUI: " + (showGUI ? "enabled" : "disabled"));
        Log.writeToLogFile("\n");
	}

	private static void runSimulation() throws IOException, JSONException {
		
		HTTPServer server = null;
		if(showGUI) {
            server = new HTTPServer();
            Log.writeToLogFile("Hosting the HTTP Server on " + server.addr());
            if(!Desktop.isDesktopSupported())
                Log.writeToLogFile("Desktop operations not supported!");
            else if(!Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                Log.writeToLogFile("Desktop browse operation not supported!");
            else {
                try {
                    Desktop.getDesktop().browse(new URI("http://localhost:" + server.port()));
                } catch(URISyntaxException e) {}
            }
        }
		
		for(int i = 0; i <= rounds; i++) {
			currentRound = i;
			Map<Integer, List<Game>> roundGamesMap = new HashMap<>();
			
			if(currentRound == 0) {   // Round 0: simulator game randomization
				generateRandomGameGrid();
				for(PlayerWrapper playerWrapper : playerWrappers) {
					int playerTeamID = playerWrapper.getPlayer().teamID;
					List<Game> randomPlayerGames = assignGamesToPlayer(playerWrapper);
					roundGamesMap.put(playerTeamID, randomPlayerGames);
				}
				gameHistory.addRoundGames(currentRound, roundGamesMap);
			}
			else {   // Reallocation rounds
				for(PlayerWrapper playerWrapper : playerWrappers) {
					int playerTeamID = playerWrapper.getPlayer().teamID;
					int previousRound = currentRound - 1;
					List<Game> playerGames = gameHistory.getAllGamesMap().get(previousRound).get(playerTeamID);

					Map<Integer, List<Game>> opponentGamesMap = new HashMap<>();
					for(PlayerWrapper opponentPlayerWrapper : playerWrappers) {
						int opponentTeamID = opponentPlayerWrapper.getPlayer().teamID;
						if(playerTeamID == opponentTeamID)
							continue;
						List<Game> opponentGames = gameHistory.getAllGamesMap().get(previousRound).get(opponentTeamID);
						opponentGamesMap.put(opponentTeamID, opponentGames);
					}
					
					List<Game> reallocatedPlayerGames = 
							playerWrapper.reallocate(currentRound, deepClone(gameHistory), deepClone(playerGames), deepClone(opponentGamesMap));
					
					List<Game> newReallocatedPlayerGames = new ArrayList<>();
					for(Game playerGame : playerGames) {
						boolean reallocationOccurred = false;
						for(Game reallocatedPlayerGame : reallocatedPlayerGames)
							if(playerGame.getID().equals(reallocatedPlayerGame.getID())) {
								newReallocatedPlayerGames.add(reallocatedPlayerGame);
								reallocationOccurred = true;
								break;
							}
						
						if(!reallocationOccurred)
							newReallocatedPlayerGames.add(playerGame);
					}
					
					if(!Player.checkConstraintsSatisfied(playerGames, newReallocatedPlayerGames))
						roundGamesMap.put(playerTeamID, deepClone(playerGames));
					else
						roundGamesMap.put(playerTeamID, deepClone(newReallocatedPlayerGames));
					
					// Reset team ID, in case it was modified during reallocation
					playerWrapper.getPlayer().teamID = (Integer) playerTeamID;
				}
				
				// Update teams' reallocated games with opponents' reallocations
				for(Integer playerTeamID : roundGamesMap.keySet()) {
					List<Game> playerGames = roundGamesMap.get(playerTeamID);
					for(Game playerGame : playerGames)
						for(Game opponentGame : roundGamesMap.get(playerGame.getID())) {
							if(playerTeamID.equals(opponentGame.getID())) {
								opponentGame.setNumOpponentGoals(playerGame.getNumPlayerGoals());
								break;
							}
						}
				}
				
				Map<Integer, PlayerPoints> roundPointsMap = computeTeamPoints(roundGamesMap);
				Map<Integer, PlayerPoints> roundCumulativePointsMap = computeCumulativeTeamPoints(roundPointsMap);
				Map<Integer, Double> roundRankingsMap = computeRoundRankings(roundPointsMap);
				Map<Integer, Double> roundAverageRankingsMap = computeAverageRankings(roundRankingsMap);
				updateGameHistory(currentRound, roundGamesMap, roundPointsMap, roundCumulativePointsMap, roundRankingsMap, roundAverageRankingsMap);	

				Log.writeToVerboseLogFile("---------------------------------------------------------Round " + currentRound + " Results----------------------------------------------------------------");
				Log.writeToVerboseLogFile("Team\t\tRound Rank\tAverage Rank\tRound Points\tCumulative Points\tMatches\tWins\tLosses\tDraws");
	        
				Map<Integer, Double> orderedRoundRankingsMap = gameHistory.getAllRoundRankingsMap().get(currentRound).entrySet()
						  .stream()
						  .sorted(Map.Entry.comparingByValue())
						  .collect(Collectors.toMap(
						    Map.Entry::getKey, 
						    Map.Entry::getValue,
						    (oldRank, newRank) -> oldRank, LinkedHashMap::new));

				Map<Integer, Double> orderedRoundAverageRankingsMap = gameHistory.getAllAverageRankingsMap().get(currentRound).entrySet()
						  .stream()
						  .sorted(Map.Entry.comparingByValue())
						  .collect(Collectors.toMap(
						    Map.Entry::getKey, 
						    Map.Entry::getValue,
						    (oldRank, newRank) -> oldRank, LinkedHashMap::new));
				
				DecimalFormat rankFormat = new DecimalFormat("###.####");
				for(Integer teamID : orderedRoundRankingsMap.keySet()) {
					int numWins = 0, numLosses = 0, numDraws = 0;
					for(Game game : roundGamesMap.get(teamID)) {
						if(Player.hasWonGame(game))
							numWins++;
						else if(Player.hasLostGame(game))
							numLosses++;
						else if(Player.hasDrawnGame(game))
							numDraws++;						
					}
					
					for(PlayerWrapper playerWrapper : playerWrappers) {
						String tabs = playerWrapper.getPlayerName().length() < 5 ? "\t\t" : "\t";
						if(playerWrapper.getPlayer().teamID.equals(teamID)) {
							Log.writeToVerboseLogFile(playerWrapper.getPlayerName() + tabs + 
											   rankFormat.format(orderedRoundRankingsMap.get(teamID)) + "\t\t" +
											   rankFormat.format(roundAverageRankingsMap.get(teamID)) + "\t\t" +
											   roundPointsMap.get(teamID) + "\t\t" +
											   roundCumulativePointsMap.get(teamID) + "\t\t\t" +
											   (numWins + numLosses + numDraws) + "\t" +
											   numWins + "\t" +
											   numLosses + "\t" + 
											   numDraws + "\t"
							);
							break;
						}
					}
				}

				Log.writeToVerboseLogFile("---------------------------------------------------------End of Round " + currentRound + "-----------------------------------------------------------------");			
				
				if(showGUI)
					updateGUI(server, getGUIState(currentRound,
												  roundGamesMap,
												  roundPointsMap,
												  roundCumulativePointsMap,
												  roundRankingsMap,
												  orderedRoundRankingsMap,
												  roundAverageRankingsMap,
												  orderedRoundAverageRankingsMap));
			}
		}
		
		Log.writeToLogFile("All rounds and reallocations have completed!\n\n");
		Log.writeToLogFile("-------------------------------------------------------------Overall Results-------------------------------------------------------------");
		Log.writeToLogFile("Team\t\tFinal Rank\tTotal Points\tMatches\tWins\tLosses\tDraws\tGoals For\tGoals Against\tGoal Difference");

		Map<Integer, Double> finalRankingsMap = gameHistory.getAllAverageRankingsMap().get(rounds).entrySet()
				  .stream()
				  .sorted(Map.Entry.comparingByValue())
				  .collect(Collectors.toMap(
				    Map.Entry::getKey, 
				    Map.Entry::getValue,
				    (oldRank, newRank) -> oldRank, LinkedHashMap::new));
		Map<Integer, PlayerPoints> finalCumulativePointsMap = gameHistory.getAllCumulativePointsMap().get(rounds);
		Map<Integer, Map<Integer, List<Game>>> allGamesMap = gameHistory.getAllGamesMap();
		
		DecimalFormat rankFormat = new DecimalFormat("###.####");
		for(Integer teamID : finalRankingsMap.keySet()) {
			int numWins = 0, numLosses = 0, numDraws = 0, numGoalsFor = 0, numGoalsAgainst = 0;
			for(int round = 1; round <= rounds; round++) {
				for(Game game : allGamesMap.get(round).get(teamID)) {
					if(Player.hasWonGame(game))
						numWins++;
					else if(Player.hasLostGame(game))
						numLosses++;
					else if(Player.hasDrawnGame(game))
						numDraws++;
					
					numGoalsFor += game.getNumPlayerGoals();
					numGoalsAgainst += game.getNumOpponentGoals();
				}
			}
			
			for(PlayerWrapper playerWrapper : playerWrappers) {
				String tabs = playerWrapper.getPlayerName().length() < 5 ? "\t\t" : "\t";
				if(playerWrapper.getPlayer().teamID.equals(teamID)) {
					Log.writeToLogFile(playerWrapper.getPlayerName() + tabs + 
									   rankFormat.format(finalRankingsMap.get(teamID)) + "\t\t" +
									   finalCumulativePointsMap.get(teamID) + "\t\t" +
									   (numWins + numLosses + numDraws) + "\t" +
									   numWins + "\t" +
									   numLosses + "\t" + 
									   numDraws + "\t" +
									   numGoalsFor + "\t\t" +
									   numGoalsAgainst + "\t\t" +
									   (numGoalsFor - numGoalsAgainst)
					);
					break;
				}
			}
		}

		Log.writeToLogFile("----------------------------------------------------------------End of Log---------------------------------------------------------------");
		Log.closeLogFile();
				
		if(exportCSV) {
			List<List<String>> rows = new ArrayList<>();
			for(int round : allGamesMap.keySet()) {
				if(round == 0)
					continue;
				for(int teamID : allGamesMap.get(round).keySet()) {
					for(Game game : allGamesMap.get(round).get(teamID)) {
						int opponentID = game.getID();
						int goalsFor = game.getNumPlayerGoals();
						int goalsAgainst = game.getNumOpponentGoals();
						int goalDifference = goalsFor - goalsAgainst;
						int pointsFor, pointsAgainst;
						if(Player.hasWonGame(game)) {
							pointsFor = PlayerPoints.getWinPointValue();
							pointsAgainst = PlayerPoints.getLossPointValue();
						}
						else if(Player.hasLostGame(game)) {
							pointsFor = PlayerPoints.getLossPointValue();
							pointsAgainst = PlayerPoints.getWinPointValue();
						}
						else {
							pointsFor = PlayerPoints.getDrawPointValue();
							pointsAgainst = PlayerPoints.getDrawPointValue();							
						}
						List<String> row = Arrays.asList(Integer.toString(round),
														 playerWrappers.get(teamID - 1).getPlayerName(),
														 playerWrappers.get(opponentID - 1).getPlayerName(),
														 Integer.toString(goalsFor),
														 Integer.toString(goalsAgainst),
														 Integer.toString(goalDifference),
														 Integer.toString(pointsFor),
														 Integer.toString(pointsAgainst));
						rows.add(row);
					}
				}
			}
			
			FileWriter csvWriter = new FileWriter(csvPath);
			csvWriter.append("Round,Team,Opponent,GF,GA,GD,PF,PA\n");
			for(List<String> row : rows)
				csvWriter.append(String.join(",", row) + "\n");
			csvWriter.flush();
			csvWriter.close();
		}
		
		if(!showGUI)
			System.exit(1);
	}

	private static Map<Integer, PlayerPoints> computeTeamPoints(Map<Integer, List<Game>> roundGamesMap) {
		Map<Integer, PlayerPoints> roundPointsMap = new HashMap<>();
		for(Map.Entry<Integer, List<Game>> gamesEntry : roundGamesMap.entrySet()) {
			PlayerPoints playerPoints = new PlayerPoints();
			for(Game game : gamesEntry.getValue()) {
				if(Player.hasWonGame(game))
					playerPoints.addWinPoints();
				else if(Player.hasLostGame(game))
					playerPoints.addLossPoints();
				else if(Player.hasDrawnGame(game))
					playerPoints.addDrawPoints();
			}
			roundPointsMap.put(gamesEntry.getKey(), playerPoints);
		}
		return roundPointsMap;
	}

	private static Map<Integer, PlayerPoints> computeCumulativeTeamPoints(Map<Integer, PlayerPoints> roundPointsMap) {
		Map<Integer, PlayerPoints> roundCumulativePointsMap = new HashMap<>();
		
		Map<Integer, Map<Integer, PlayerPoints>> allCumulativePointsMap = gameHistory.getAllCumulativePointsMap();
		for(Map.Entry<Integer, PlayerPoints> newPointsEntry : roundPointsMap.entrySet()) {
			int roundCumulativePoints = newPointsEntry.getValue().getTotalPoints();
			int mapSize = allCumulativePointsMap.size();
			roundCumulativePoints += mapSize == 0 ? 0 : allCumulativePointsMap.get(mapSize).get(newPointsEntry.getKey()).getTotalPoints();
			roundCumulativePointsMap.put(newPointsEntry.getKey(), new PlayerPoints(roundCumulativePoints));
		}
		
		return roundCumulativePointsMap;
	}
	
	private static Map<Integer, Double> computeRoundRankings(Map<Integer, PlayerPoints> roundPointsMap) {
		Map<Integer, Double> rankingsMap = new HashMap<>();
		
		Map<Integer, PlayerPoints> rankedPointsMap = roundPointsMap.entrySet()
				  .stream()
				  .sorted(Map.Entry.comparingByValue())
				  .collect(Collectors.toMap(
				    Map.Entry::getKey, 
				    Map.Entry::getValue,
				    (oldPoints, newPoints) -> oldPoints, LinkedHashMap::new));
						
		List<Integer> rankedTeamIDs = new ArrayList<>(rankedPointsMap.keySet());
		Collections.reverse(rankedTeamIDs);
				
		int indexOfFirstTiedElement = 0;
		for(int i = 0; i < rankedTeamIDs.size() - 1; i++) {
			int currTeamID = rankedTeamIDs.get(i);
			int currTeamNumPoints = rankedPointsMap.get(currTeamID).getTotalPoints();
			int nextTeamID = rankedTeamIDs.get(i + 1);
			int nextTeamNumPoints = rankedPointsMap.get(nextTeamID).getTotalPoints();
			
			if(currTeamNumPoints == nextTeamNumPoints)
				continue;
			else if(indexOfFirstTiedElement != i) {
				double averageRank = ((double) (indexOfFirstTiedElement + i)) / 2 + 1;
				for(int j = indexOfFirstTiedElement; j <= i; j++)
					rankingsMap.put(rankedTeamIDs.get(j), averageRank);
				indexOfFirstTiedElement = i + 1;
			}
			else {
				rankingsMap.put(currTeamID, (double) (i + 1));
				indexOfFirstTiedElement = i + 1;
			}
		}
		
		if(indexOfFirstTiedElement != rankedTeamIDs.size() - 1) {
			double averageRank = ((double) (indexOfFirstTiedElement + rankedTeamIDs.size() - 1)) / 2 + 1;
			for(int i = indexOfFirstTiedElement; i < rankedTeamIDs.size(); i++)
				rankingsMap.put(rankedTeamIDs.get(i), averageRank);
		}
		else
			rankingsMap.put(rankedTeamIDs.get(rankedTeamIDs.size() - 1), (double) rankedTeamIDs.size());

		return rankingsMap;
	}	

	private static Map<Integer, Double> computeAverageRankings(Map<Integer, Double> roundPointsMap) {
		Map<Integer, Double> averageRankingsMap = new HashMap<>();
		
		Map<Integer, Map<Integer, Double>> allRoundRankingsMap = gameHistory.getAllRoundRankingsMap();
		for(int teamID : roundPointsMap.keySet()) {
			double rankSum = roundPointsMap.get(teamID);
			for(int round : allRoundRankingsMap.keySet())
				rankSum += allRoundRankingsMap.get(round).get(teamID);
			double rankAverage = rankSum / (allRoundRankingsMap.size() + 1);
			averageRankingsMap.put(teamID, rankAverage);
		}
		
		return averageRankingsMap;
	}

	private static void updateGameHistory(Integer round,
										  Map<Integer, List<Game>> roundGamesMap,
										  Map<Integer, PlayerPoints> roundPointsMap,
										  Map<Integer, PlayerPoints> roundCumulativePointsMap,
										  Map<Integer, Double> roundRankingsMap,
										  Map<Integer, Double> roundAverageRankingsMap) {
		gameHistory.addRoundGames(round, roundGamesMap);
		gameHistory.addRoundPoints(round, roundPointsMap);
		gameHistory.addRoundCumulativePoints(round, roundCumulativePointsMap);
		gameHistory.addRoundRankings(round, roundRankingsMap);
		gameHistory.addRoundAverageRankings(round, roundAverageRankingsMap);
	}
	
	private static String cleanName(String playerName) {
		String cleanedPlayerName = " ";
		if(playerName.contains("_")) {
			Integer index = playerName.lastIndexOf("_");
			cleanedPlayerName = playerName.substring(0, index);
		}
		else
			return playerName;

		return cleanedPlayerName;
	}
	
	private static void generateRandomGameGrid() {
		List<Integer> goalPossibilities = new ArrayList<>();
		randomGameGrid = new Integer[playerWrappers.size()][playerWrappers.size()];
		for(int i = 0; i < randomGameGrid.length; i++) {
			for(int j = 0; j <= Math.max(playerWrappers.size() - 2, Game.getMaxGoalThreshold()); j++)
				goalPossibilities.add(j);
			for(int j = 0; j < randomGameGrid[i].length; j++) {
				if(i == j)
					randomGameGrid[i][j] = 0;
				else {
					int index = random.nextInt(goalPossibilities.size());
					randomGameGrid[i][j] = goalPossibilities.get(index);
					goalPossibilities.remove(index);
				}
			}
			goalPossibilities.clear();
		}		
	}
	
	private static List<Game> assignGamesToPlayer(PlayerWrapper playerWrapper) {
		List<Game> playerGames = new ArrayList<>();
		
		int indexOfPlayerWrapper = playerWrappers.indexOf(playerWrapper);
		for(int i = 0; i < randomGameGrid[indexOfPlayerWrapper].length; i++) {
			if(i == indexOfPlayerWrapper)
				continue;

			int gameID = i + 1;
			int numPlayerGoals = randomGameGrid[indexOfPlayerWrapper][i];
			int numOpponentGoals = randomGameGrid[i][indexOfPlayerWrapper];
			
			Game game = new Game(gameID, new Score(numPlayerGoals, numOpponentGoals));
			playerGames.add(game);
		}
		
		return playerGames;
	}
	
	private static <T extends Object> T deepClone(T obj) {
        if(obj == null)
            return null;

        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(obj);
            ByteArrayInputStream bais = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
            ObjectInputStream objectInputStream = new ObjectInputStream(bais);
            
            return (T) objectInputStream.readObject();
        }
        catch(Exception e) {
            return null;
        }
	}
	
	private static PlayerWrapper loadPlayerWrapper(String playerName, String modifiedPlayerName) throws Exception {
		Log.writeToLogFile("Loading team " + playerName + "...");

		int teamID = playerWrappers.size() + 1;
		Player player = loadPlayer(playerName, teamID);
        if(player == null) {
            Log.writeToLogFile("Cannot load team " + playerName + "!");
            System.exit(1);
        }

        return new PlayerWrapper(player, modifiedPlayerName, timeout);
    }
	
	private static Player loadPlayer(String playerName, int teamID) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		String playerPackagePath = sourcePath + File.separator + playerName;
        Set<File> playerFiles = getFilesInDirectory(playerPackagePath, ".java");
		String simPath = sourcePath + File.separator + "sim";
        Set<File> simFiles = getFilesInDirectory(simPath, ".java");

        File classFile = new File(playerPackagePath + File.separator + "Player.class");

        long classModified = classFile.exists() ? classFile.lastModified() : -1;
        if(classModified < 0 || classModified < lastModified(playerFiles) || classModified < lastModified(simFiles)) {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if(compiler == null)
                throw new IOException("Cannot find the Java compiler!");

            StandardJavaFileManager manager = compiler.getStandardFileManager(null, null, null);
            Log.writeToLogFile("Compiling for team " + playerName + "...");

            if(!compiler.getTask(null, manager, null, null, null, manager.getJavaFileObjectsFromFiles(playerFiles)).call())
                throw new IOException("The compilation failed!");
            
            classFile = new File(playerPackagePath + File.separator + "Player.class");
            if(!classFile.exists())
                throw new FileNotFoundException("The class file is missing!");
        }

        ClassLoader loader = Simulator.class.getClassLoader();
        if(loader == null)
            throw new IOException("Cannot find the Java class loader!");

        @SuppressWarnings("rawtypes")
        Class rawClass = loader.loadClass(playerName + ".Player");
        Class[] classArgs = new Class[]{Integer.class, Integer.class, Integer.class, SimPrinter.class};

        return (Player) rawClass.getDeclaredConstructor(classArgs).newInstance(teamID, rounds, seed, new SimPrinter(enablePrints));
    }

	private static long lastModified(Iterable<File> files) {
        long lastDate = 0;
        for(File file : files) {
            long date = file.lastModified();
            if(lastDate < date)
                lastDate = date;
        }
        return lastDate;
    }
	
	private static Set<File> getFilesInDirectory(String path, String extension) {
		Set<File> files = new HashSet<File>();
        Set<File> previousDirectories = new HashSet<File>();
        previousDirectories.add(new File(path));
        do {
        	Set<File> nextDirectories = new HashSet<File>();
            for(File previousDirectory : previousDirectories)
                for(File file : previousDirectory.listFiles()) {
                    if(!file.canRead())
                    	continue;
                    
                    if(file.isDirectory())
                        nextDirectories.add(file);
                    else if(file.getPath().endsWith(extension))
                        files.add(file);
                }
            previousDirectories = nextDirectories;
        } while(!previousDirectories.isEmpty());
        
        return files;
	}
	
	private static void updateGUI(HTTPServer server, String content) {
		if(server == null)
			return;
		
        String guiPath = null;
        while(true) {
            while(true) {
                try {
                	guiPath = server.request();
                    break;
                } catch(IOException e) {
                    Log.writeToVerboseLogFile("HTTP request error: " + e.getMessage());
                }
            }
            
            if(guiPath.equals("data.txt")) {
                try {
                    server.reply(content);
                } catch(IOException e) {
                    Log.writeToVerboseLogFile("HTTP dynamic reply error: " + e.getMessage());
                }
                return;
            }
            
            if(guiPath.equals(""))
            	guiPath = "webpage.html";
            else if(!Character.isLetter(guiPath.charAt(0))) {
                Log.writeToVerboseLogFile("Potentially malicious HTTP request: \"" + guiPath + "\"");
                break;
            }

            try {
                File file = new File(staticsPath + File.separator + guiPath);
                server.reply(file);
            } catch(IOException e) {
                Log.writeToVerboseLogFile("HTTP static reply error: " + e.getMessage());
            }
        }		
	}
	
	private static String getGUIState(int round,
									  Map<Integer, List<Game>> roundGamesMap,
									  Map<Integer, PlayerPoints> roundPointsMap,
									  Map<Integer, PlayerPoints> roundCumulativePointsMap,
									  Map<Integer, Double> roundRankingsMap,
									  Map<Integer, Double> orderedRoundRankingsMap,
									  Map<Integer, Double> roundAverageRankingsMap,
									  Map<Integer, Double> orderedRoundAverageRankingsMap) throws JSONException {

		Map<Integer, Map<Integer, List<Game>>> allGamesMap = gameHistory.getAllGamesMap(); 
		Map<Integer, Integer> numCumulativeWinsMap = new HashMap<>();
		Map<Integer, Integer> numCumulativeLossesMap = new HashMap<>();
		Map<Integer, Integer> numCumulativeDrawsMap = new HashMap<>();
		Map<Integer, Integer> numRoundWinsMap = new HashMap<>();
		Map<Integer, Integer> numRoundLossesMap = new HashMap<>();
		Map<Integer, Integer> numRoundDrawsMap = new HashMap<>();
		Map<Integer, Integer> numCumulativeGoalsForMap = new HashMap<>();
		Map<Integer, Integer> numCumulativeGoalsAgainstMap = new HashMap<>();
		
		for(int gameRound : allGamesMap.keySet()) {
			if(gameRound == 0)
				continue;
			
			for(int teamID : allGamesMap.get(gameRound).keySet()) {
				if(!numCumulativeWinsMap.containsKey(teamID))
					numCumulativeWinsMap.put(teamID, 0);
				if(!numCumulativeDrawsMap.containsKey(teamID))
					numCumulativeDrawsMap.put(teamID, 0);
				if(!numCumulativeLossesMap.containsKey(teamID))
					numCumulativeLossesMap.put(teamID, 0);
				if(!numCumulativeGoalsForMap.containsKey(teamID))
					numCumulativeGoalsForMap.put(teamID, 0);
				if(!numCumulativeGoalsAgainstMap.containsKey(teamID))
					numCumulativeGoalsAgainstMap.put(teamID, 0);
				for(Game game : allGamesMap.get(gameRound).get(teamID)) {
					if(Player.hasWonGame(game))
						numCumulativeWinsMap.put(teamID, numCumulativeWinsMap.get(teamID) + 1);
					else if(Player.hasDrawnGame(game))
						numCumulativeDrawsMap.put(teamID, numCumulativeDrawsMap.get(teamID) + 1);
					else if(Player.hasLostGame(game))
						numCumulativeLossesMap.put(teamID, numCumulativeLossesMap.get(teamID) + 1);

					numCumulativeGoalsForMap.put(teamID, numCumulativeGoalsForMap.get(teamID) + game.getNumPlayerGoals());
					numCumulativeGoalsAgainstMap.put(teamID, numCumulativeGoalsAgainstMap.get(teamID) + game.getNumOpponentGoals());
				}
			}
		}
		
		for(int teamID : roundGamesMap.keySet()) {
			if(!numRoundWinsMap.containsKey(teamID))
				numRoundWinsMap.put(teamID, 0);
			if(!numRoundDrawsMap.containsKey(teamID))
				numRoundDrawsMap.put(teamID, 0);
			if(!numRoundLossesMap.containsKey(teamID))
				numRoundLossesMap.put(teamID, 0);
			for(Game game : roundGamesMap.get(teamID)) {
				if(Player.hasWonGame(game))
					numRoundWinsMap.put(teamID, numRoundWinsMap.get(teamID) + 1);
				else if(Player.hasDrawnGame(game))
					numRoundDrawsMap.put(teamID, numRoundDrawsMap.get(teamID) + 1);
				else if(Player.hasLostGame(game))
					numRoundLossesMap.put(teamID, numRoundLossesMap.get(teamID) + 1);
			}				
		}
		
		JSONObject jsonObj = new JSONObject();
		jsonObj.put("refresh", 60000.0 / fpm);
		jsonObj.put("totalRounds", rounds);
		jsonObj.put("currentRound", round);
		jsonObj.put("continuous", continuousGUI);
		
		JSONObject roundGamesJSONObj = new JSONObject();
		JSONObject roundPointsJSONObj = new JSONObject();
		JSONObject roundCumulativePointsJSONObj = new JSONObject();
		JSONObject roundRankingsJSONObj = new JSONObject();
		JSONObject numCumulativeWinsJSONObj = new JSONObject();
		JSONObject numCumulativeDrawsJSONObj = new JSONObject();
		JSONObject numCumulativeLossesJSONObj = new JSONObject();
		JSONObject numCumulativeGoalsForJSONObj = new JSONObject();
		JSONObject numCumulativeGoalsAgainstJSONObj = new JSONObject();		
		JSONObject numRoundWinsJSONObj = new JSONObject();
		JSONObject numRoundDrawsJSONObj = new JSONObject();
		JSONObject numRoundLossesJSONObj = new JSONObject();
		JSONArray orderedRoundRankingsJSONArray = new JSONArray();
		JSONObject roundAverageRankingsJSONObj = new JSONObject();
		JSONArray orderedRoundAverageRankingsJSONArray = new JSONArray();

		DecimalFormat rankFormat = new DecimalFormat("###.####");

		for(Integer teamID : roundGamesMap.keySet()) {			
			JSONObject teamGamesJSONObj = new JSONObject();
			for(Game game : roundGamesMap.get(teamID)) {
				int gameID = game.getID();
				int numPlayerGoals = game.getNumPlayerGoals();
				int numOpponentGoals = game.getNumOpponentGoals();

				JSONObject teamGamesNestedJSONObj = new JSONObject();
				teamGamesNestedJSONObj.put("playerGoals", numPlayerGoals);
				teamGamesNestedJSONObj.put("opponentGoals", numOpponentGoals);
				
				teamGamesJSONObj.put(playerWrappers.get(gameID - 1).getPlayerName(), teamGamesNestedJSONObj);
			}
			
			String playerName = playerWrappers.get(teamID - 1).getPlayerName();
			roundGamesJSONObj.put(playerName, teamGamesJSONObj);
			roundPointsJSONObj.put(playerName, Integer.parseInt(roundPointsMap.get(teamID).toString()));
			roundCumulativePointsJSONObj.put(playerName, Integer.parseInt(roundCumulativePointsMap.get(teamID).toString()));
		  	roundRankingsJSONObj.put(playerName, rankFormat.format(roundRankingsMap.get(teamID)));
		  	numCumulativeWinsJSONObj.put(playerName, numCumulativeWinsMap.get(teamID));
		  	numCumulativeDrawsJSONObj.put(playerName, numCumulativeDrawsMap.get(teamID));
		  	numCumulativeLossesJSONObj.put(playerName, numCumulativeLossesMap.get(teamID));
		  	numRoundWinsJSONObj.put(playerName, numRoundWinsMap.get(teamID));
		  	numRoundDrawsJSONObj.put(playerName, numRoundDrawsMap.get(teamID));
		  	numRoundLossesJSONObj.put(playerName, numRoundLossesMap.get(teamID));
		  	numCumulativeGoalsForJSONObj.put(playerName, numCumulativeGoalsForMap.get(teamID));
		  	numCumulativeGoalsAgainstJSONObj.put(playerName, numCumulativeGoalsAgainstMap.get(teamID));
		  	roundAverageRankingsJSONObj.put(playerName, rankFormat.format(roundAverageRankingsMap.get(teamID)));
		}
		for(Integer teamID : orderedRoundRankingsMap.keySet()) {
			JSONObject orderedRoundRankingsJSONObj = new JSONObject();
			orderedRoundRankingsJSONObj.put("team", playerWrappers.get(teamID - 1).getPlayerName());
			orderedRoundRankingsJSONObj.put("ranking", rankFormat.format(orderedRoundRankingsMap.get(teamID)));
			orderedRoundRankingsJSONArray.put(orderedRoundRankingsJSONObj);
		}
		for(Integer teamID : orderedRoundAverageRankingsMap.keySet()) {
			JSONObject orderedRoundAverageRankingsJSONObj = new JSONObject();
			orderedRoundAverageRankingsJSONObj.put("team", playerWrappers.get(teamID - 1).getPlayerName());
			orderedRoundAverageRankingsJSONObj.put("ranking", rankFormat.format(orderedRoundAverageRankingsMap.get(teamID)));
			orderedRoundAverageRankingsJSONArray.put(orderedRoundAverageRankingsJSONObj);
		}
		
		jsonObj.put("games", roundGamesJSONObj);
		jsonObj.put("points", roundPointsJSONObj);
		jsonObj.put("cumulativePoints", roundCumulativePointsJSONObj);
		jsonObj.put("rankings", roundRankingsJSONObj);
		jsonObj.put("cumulativeWins", numCumulativeWinsJSONObj);
		jsonObj.put("cumulativeDraws", numCumulativeDrawsJSONObj);
		jsonObj.put("cumulativeLosses", numCumulativeLossesJSONObj);
		jsonObj.put("cumulativeGoalsFor", numCumulativeGoalsForJSONObj);
		jsonObj.put("cumulativeGoalsAgainst", numCumulativeGoalsAgainstJSONObj);
		jsonObj.put("roundWins", numRoundWinsJSONObj);
		jsonObj.put("roundDraws", numRoundDrawsJSONObj);
		jsonObj.put("roundLosses", numRoundLossesJSONObj);
		jsonObj.put("orderedRankings", orderedRoundRankingsJSONArray);
		jsonObj.put("averageRankings", roundAverageRankingsJSONObj);
		jsonObj.put("orderedAverageRankings", orderedRoundAverageRankingsJSONArray);
				
        return jsonObj.toString();
	}
	
	public static void main(String[] args) throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException, JSONException {
		setup();
		parseCommandLineArguments(args);
		runSimulation();
	}
}