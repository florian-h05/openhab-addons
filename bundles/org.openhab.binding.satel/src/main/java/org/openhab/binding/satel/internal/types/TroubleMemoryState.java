/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.satel.internal.types;

/**
 * Supported memory of troubles.
 *
 * @author Krzysztof Goworek - Initial contribution
 */
public enum TroubleMemoryState implements StateType {
    // part 1
    TECHNICAL_ZONE(0x20, 47, 0, 16),
    EXPANDER_AC(0x20, 47, 16, 8),
    EXPANDER_BATT(0x20, 47, 24, 8),
    EXPANDER_NOBATT(0x20, 47, 32, 8),
    SYSTEM(0x20, 47, 40, 3),
    PTSA_AC(0x20, 47, 43, 1),
    PTSA_BATT(0x20, 47, 44, 1),
    PTSA_NOBATT(0x20, 47, 45, 1),
    ETHM1(0x20, 47, 46, 1),
    // part 2
    PROXIMITY_A(0x21, 39, 0, 8),
    PROXIMITY_B(0x21, 39, 8, 8),
    EXPANDER_OVERLOAD(0x21, 39, 16, 8),
    JAMMED_ACU100(0x21, 39, 24, 2),
    LCD_RESTART(0x21, 39, 26, 1),
    EXPANDER_RESTART(0x21, 39, 27, 8),
    // part 3
    DEVICE_LOBATT(0x22, 60, 15, 15),
    DEVICE_NOCOMM(0x22, 60, 30, 15),
    OUTPUT_NOCOMM(0x22, 60, 45, 15),
    // part 4
    EXPANDER_NOCOMM(0x23, 30, 0, 8),
    EXPANDER_SWITCHEROOED(0x23, 30, 8, 8),
    KEYPAD_NOCOMM(0x23, 30, 16, 1),
    KEYPAD_SWITCHEROOED(0x23, 30, 17, 1),
    ETHM1_NOLAN(0x23, 30, 18, 1),
    EXPANDER_TAMPER(0x23, 30, 19, 8),
    KEYPAD_TAMPER(0x23, 30, 27, 1),
    KEYPAD_INIT(0x23, 30, 28, 1),
    AUXILIARY_STM(0x23, 30, 29, 1),
    // part 5
    LONG_VIOLATION(0x24, 48, 0, 16),
    NO_VIOLATION(0x24, 48, 16, 16),
    ZONE_TAMPER(0x24, 48, 32, 16),
    // part 7 of troubles
    TECHNICAL_ZONE1(0x2d, 47, 16, 16),
    // part 6
    DEVICE_LOBATT1(0x2e, 45, 0, 15),
    DEVICE_NOCOMM1(0x2e, 45, 15, 15),
    OUTPUT_NOCOMM1(0x2e, 45, 30, 15),
    // part 7
    LONG_VIOLATION1(0x2f, 48, 0, 16),
    NO_VIOLATION1(0x2f, 48, 16, 16),
    ZONE_TAMPER1(0x2f, 48, 32, 16),;

    private byte refreshCommand;
    private int payloadLength;
    private int startByte;
    private int bytesCount;

    TroubleMemoryState(int refreshCommand, int payloadLength, int startByte, int bytesCount) {
        this.refreshCommand = (byte) refreshCommand;
        this.payloadLength = payloadLength;
        this.startByte = startByte;
        this.bytesCount = bytesCount;
    }

    @Override
    public byte getRefreshCommand() {
        return refreshCommand;
    }

    @Override
    public int getPayloadLength(boolean extendedCmd) {
        return payloadLength;
    }

    @Override
    public ObjectType getObjectType() {
        return ObjectType.TROUBLE_MEMORY;
    }

    @Override
    public int getStartByte() {
        return startByte;
    }

    @Override
    public int getBytesCount(boolean isExtended) {
        return bytesCount;
    }
}
