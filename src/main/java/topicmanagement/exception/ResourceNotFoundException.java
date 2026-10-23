package topicmanagement.exception;

/**
 * Custom runtime exception for missing entities (e.g. Topic, User, Period).
 * Reference: Chapter 06 Slide 35 (Custom Exception extending RuntimeException for automatic rollback).
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
