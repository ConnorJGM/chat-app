// NumberGame.java

package com.connorgillmead.chat.server.utilities;

import java.util.Random;

/**
 * Represents a number guessing game.
 * This class is responsible for managing the game state, including the target number,
 * the owner of the game, and the active status of the game.
 * It provides methods for starting the game, checking guesses, and determining the winner.
 */
public class NumberGame {
    // Variables to store the owner of the game, target number, and active status.
    private final String owner;
    private final int    targetNumber;
    private boolean      active = true;
    private int          bound = 100;

    // Constructor for NumberGame.
    // This constructor initializes the owner of the game and generates a random target number between 1 and 100.
    public NumberGame(String owner) {
        this.owner = owner;
        this.targetNumber = new Random().nextInt(bound) + 1;
    }

    // Returns the owner of the game.
    // This method is used to identify the player who created the game.
    public String getOwner() {
        return owner;
    }

    // Returns the target number for the game.
    // This method is used to check the player's guess against the target number.
    public boolean isActive() {
        return active;
    }

    // Starts the game and returns a message indicating the game has started.
    // This method is called when the game is created.
    public String getStartMessage() {
        return String.format("%s has started the number game. Good luck!", owner);
    }

    /**
     * Checks the player's guess against the target number.
     * This method is called when a player makes a guess.
     * @param player The name of the player making the guess.
     * @param guess The player's guess.
     * @return A message indicating whether the guess is too low, too high, or correct.
     *         If the guess is correct, the game is marked as inactive.
     */
    public String checkGuess(String player, int guess) {
        if (guess == targetNumber) {
            active = false;
            return getWinMessage(player);
        } else if (guess < targetNumber) {
            return String.format("%s's guess of %d is too low. Try again!", player, guess);
        } else {
            return String.format("%s's guess of %d is too high. Try again!", player, guess);
        }
    }

    // Returns a message indicating the player has won the game.
    // This method is called when a player guesses the correct number.
    public String getWinMessage(String winner) {
        active = false;
        return String.format("Congratulations! %s guessed the number %d correctly!", winner, targetNumber);
    }
}
