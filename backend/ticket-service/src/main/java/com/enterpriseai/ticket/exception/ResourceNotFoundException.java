package com.enterpriseai.ticket.exception;

/**
 * Thrown when a requested resource is not found within the current tenant scope.
 * HTTP 404 is mapped in the global exception handler.
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceType;
    private final String id;

    public ResourceNotFoundException(String resourceType, String id) {
        super(String.format("%s not found: %s", resourceType, id));
        this.resourceType = resourceType;
        this.id = id;
    }

    public String getResourceType() { return resourceType; }
    public String getId() { return id; }
}

