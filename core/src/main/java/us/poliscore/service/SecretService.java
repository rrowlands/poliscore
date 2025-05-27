
package us.poliscore.service;

import java.util.Optional;

public interface SecretService {
    
    /**
     * Retrieve a secret value by key
     * @param key the secret key
     * @return the secret value if found, empty otherwise
     */
    Optional<String> getSecret(String key);
    
    /**
     * Set a secret value
     * @param key the secret key
     * @param value the secret value
     */
    void setSecret(String key, String value);
    
    /**
     * Check if a secret exists
     * @param key the secret key
     * @return true if the secret exists, false otherwise
     */
    boolean hasSecret(String key);
    
    /**
     * Delete a secret
     * @param key the secret key
     */
    void deleteSecret(String key);
}
