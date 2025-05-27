
package us.poliscore.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.val;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class EnvironmentSecretService implements SecretService {
    
    private final Map<String, String> secrets = new ConcurrentHashMap<>();
    
    @Override
    public Optional<String> getSecret(String key) {
        // First check our internal cache
        String value = secrets.get(key);
        if (value != null) {
            return Optional.of(value);
        }
        
        // Then check environment variables
        value = System.getenv(key);
        if (value != null) {
            return Optional.of(value);
        }
        
        // Finally check system properties
        value = System.getProperty(key);
        if (value != null) {
            return Optional.of(value);
        }
        
        Log.warn("Secret not found: " + key);
        return Optional.empty();
    }
    
    @Override
    public void setSecret(String key, String value) {
        secrets.put(key, value);
    }
    
    @Override
    public boolean hasSecret(String key) {
        return getSecret(key).isPresent();
    }
    
    @Override
    public void deleteSecret(String key) {
        secrets.remove(key);
    }
}
