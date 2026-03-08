/*
 * Folia Phantom - Class Transformer Interface
 * 
 * Copyright (c) 2025 Marv
 * Licensed under MARV License
 */
package com.patch.foliaphantom.core.transformer;

import org.objectweb.asm.ClassVisitor;

/**
 * Interface for bytecode transformers that modify plugin classes.
 */
public interface ClassTransformer {
    /**
     * Creates a new ClassVisitor that applies transformation logic.
     * 
     * @param next The next visitor in the chain
     * @return A new ClassVisitor instance
     */
    ClassVisitor createVisitor(ClassVisitor next);
}
