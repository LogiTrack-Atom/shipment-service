package com.logitrack_atom.shipment_service.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Rechaza marcadores ausentes sin depender de MySQL ni Config Server.
 *
 * @author MaxDz
 * @version 0.2.0
 */
class ConfigMarkerPropertiesTest {
    /** Un marcador vacío no permite presentar una configuración incompleta como válida. */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void requiresNonBlankMarker(String marker) {
        assertThatThrownBy(() -> new ConfigMarkerProperties(marker)).isInstanceOf(IllegalArgumentException.class);
    }
}
