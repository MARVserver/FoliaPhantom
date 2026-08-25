package com.patch.foliaphantom.patcher;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

public class BlockReadBridgeTest {

    @Test
    public void exposesStackNeutralBlockReadSignatures() throws Exception {
        Method getType = BlockReadBridge.class.getMethod("getType", org.bukkit.block.Block.class);
        Method getBlockData = BlockReadBridge.class.getMethod("getBlockData", org.bukkit.block.Block.class);
        Method getState = BlockReadBridge.class.getMethod("getState", org.bukkit.block.Block.class);

        assertEquals(org.bukkit.Material.class, getType.getReturnType());
        assertEquals(org.bukkit.block.data.BlockData.class, getBlockData.getReturnType());
        assertEquals(org.bukkit.block.BlockState.class, getState.getReturnType());
    }
}
