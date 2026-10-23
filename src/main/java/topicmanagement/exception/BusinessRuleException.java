package topicmanagement.exception;

/**
 * Custom runtime exception for domain business rule violations.
 * Reference: Chapter 06 Slide 35 (Custom Exception extending RuntimeException for automatic rollback).
 */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
