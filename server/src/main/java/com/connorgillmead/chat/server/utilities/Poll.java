// Poll.java

package com.connorgillmead.chat.server.utilities;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a poll in the chat application.
 * This class contains the owner of the poll, the question, and the options.
 * It provides methods to create a poll, vote on options, and finish the poll.
 * The poll is active until it is finished.
 * The options are stored in a LinkedHashMap to maintain the order of insertion.
 * The votes for each option are stored as integers.
 */
public class Poll {
    // Variables to store the owner of the poll, question, and options.
    private final String               owner;
    private final String               question;
    private final Map<String, Integer> options = new LinkedHashMap<>();
    private boolean                    active = true;

    /**
     * Constructor for Poll.
     * This constructor initializes the owner of the poll, the question, and the options.
     * The options are stored in a LinkedHashMap to maintain the order of insertion.
     *
     * @param owner    The owner of the poll.
     * @param question The question of the poll.
     * @param options  The options for the poll.
     */
    public Poll(String owner, String question, String... options) {
        this.owner = owner;
        this.question = question;
        for (String option : options) {
            this.options.put(option, 0);
        }
    }

    /**
     * Returns the owner of the poll.
     * This method is called to retrieve the owner of the poll.
     *
     * @return The owner of the poll.
     */
    public String getOwner() {
        return owner;
    }

    /**
     * Returns whether the poll is active.
     * This method is called to check if the poll is still open for voting.
     *
     * @return True if the poll is active, false if it has been finished.
     */
    public Boolean isActive() {
        return active;
    }

    /**
     * Returns the question of the poll.
     * This method is called to retrieve the question of the poll.
     *
     * @return The question of the poll.
     */
    public String getQuestion() {
        return question;
    }

    /**
     * Returns the options of the poll.
     * This method is called to retrieve the options available for voting in the poll.
     *
     * @return A map containing the options and their corresponding vote counts.
     */
    public Map<String, Integer> getOptions() {
        return options;
    }

    /**
     * Returns the start message for the poll.
     * This method is called when the poll is created.
     *
     * @return A message indicating that the poll has started, along with the question and options.
     */
    public String getStartMessage() {
        var stringBuilder = new StringBuilder(owner)
            .append(" has started a poll: ")
            .append(question)
            .append("\nOptions:\n");
        int i = 1;
        for (String option : options.keySet()) {
            stringBuilder.append(i++).append(". ").append(option).append("\n");
        }
        stringBuilder.append("Vote by sending the option number.");
        return stringBuilder.toString();
    }

    /**
     * Casts a vote for the specified option.
     * This method is called when a user votes on the poll.
     *
     * @param voter  The name of the user casting the vote.
     * @param option The option number to vote for.
     * @return A message indicating the result of the vote.
     */
    public synchronized String vote(String voter, int option) {
        if (!active) {
            return "Poll is closed. No more votes can be cast.";
        }
        if (option < 1 || option > options.size()) {
            return "Invalid option. Please vote again.";
        }
        String optionKey = (String) options.keySet().toArray()[option - 1];
        options.put(optionKey, options.get(optionKey) + 1);
        return String.format("%s voted for %s", voter, optionKey);
    }

    /**
     * Finishes the poll and returns the results.
     * This method is called when the poll is finished.
     *
     * @return A message indicating the results of the poll.
     */
    public String finish() {
        active = false;
        var stringBuilder = new StringBuilder("Poll results:\n");
        for (Map.Entry<String, Integer> entry : options.entrySet()) {
            stringBuilder.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        return stringBuilder.toString();
    }
}
