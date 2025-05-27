
package us.poliscore.view;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LegiscanSastView {
    // SAST (Subject/Action/Status/Title) fields can be added based on API documentation
}
