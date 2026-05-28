package com.patch.foliaphantom.transformer;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import java.util.List;

/**
 * {@code Entity} / {@code LivingEntity} の書き込み操作呼び出しを
 * スレッドセーフなラッパーに置き換えるトランスフォーマー。
 *
 * <p>Folia ではエンティティ操作はそのエンティティを所有するリージョンの
 * スレッド上で行う必要がある。本トランスフォーマーは Entity の
 * 書き込み操作を {@code FoliaPatcher} のラッパーに置き換え、
 * {@code EntityScheduler} 経由で正しいスレッドにルーティングする。</p>
 *
 * <p>変換例:
 * <pre>
 *   entity.teleport(location);
 *     → FoliaPatcher.safeTeleport(entity, location);
 *
 *   entity.remove();
 *     → FoliaPatcher.safeRemove(entity);
 *
 *   livingEntity.damage(amount);
 *     → FoliaPatcher.safeDamage(livingEntity, amount);
 *
 *   livingEntity.setHealth(health);
 *     → FoliaPatcher.safeSetHealth(livingEntity, health);
 * </pre>
 * </p>
 */
public final class EntitySchedulerTransformer implements ClassTransformer, Opcodes {

    /** Entity クラスの内部名 */
    private static final String ENTITY_OWNER = "org/bukkit/entity/Entity";

    /** LivingEntity クラスの内部名 */
    private static final String LIVING_ENTITY_OWNER = "org/bukkit/entity/LivingEntity";

    /** FoliaPatcher の内部名 */
    private static final String PATCHER_OWNER = "com/patch/foliaphantom/patcher/FoliaPatcher";

    @Override
    public byte[] transform(ClassNode classNode, String className, ClassWriter writer) {
        List<MethodNode> methods = classNode.methods;
        if (methods == null) {
            classNode.accept(writer);
            return writer.toByteArray();
        }
        for (MethodNode method : methods) {
            transformMethod(method);
        }
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private void transformMethod(MethodNode method) {
        AbstractInsnNode[] insns = method.instructions.toArray();
        for (AbstractInsnNode insn : insns) {
            if (insn instanceof MethodInsnNode methodInsn) {
                replaceEntityCall(methodInsn);
            }
        }
    }

    private void replaceEntityCall(MethodInsnNode methodInsn) {
        String owner = methodInsn.owner;
        String name = methodInsn.name;
        String desc = methodInsn.desc;
        int argCount = getArgumentCount(desc);

        // Entity のメソッド
        if (ENTITY_OWNER.equals(owner)) {
            // Entity.teleport(Location) → safeTeleport(Entity, Location)
            if ("teleport".equals(name) && argCount == 1 && desc.contains("Location")) {
                methodInsn.owner = PATCHER_OWNER;
                methodInsn.name = "safeTeleport";
                methodInsn.desc = "(Lorg/bukkit/entity/Entity;Lorg/bukkit/Location;)Z";
                methodInsn.setOpcode(INVOKESTATIC);
                methodInsn.itf = false;
                return;
            }
            // Entity.teleport(Location, TeleportCause) → safeTeleport(Entity, Location, TeleportCause)
            if ("teleport".equals(name) && argCount == 2) {
                methodInsn.owner = PATCHER_OWNER;
                methodInsn.name = "safeTeleport";
                methodInsn.desc = "(Lorg/bukkit/entity/Entity;Lorg/bukkit/Location;Lorg/bukkit/event/player/PlayerTeleportEvent$TeleportCause;)Z";
                methodInsn.setOpcode(INVOKESTATIC);
                methodInsn.itf = false;
                return;
            }
            // Entity.remove() → safeRemove(Entity)
            if ("remove".equals(name)) {
                methodInsn.owner = PATCHER_OWNER;
                methodInsn.name = "safeRemove";
                methodInsn.desc = "(Lorg/bukkit/entity/Entity;)V";
                methodInsn.setOpcode(INVOKESTATIC);
                methodInsn.itf = false;
                return;
            }
            // Entity.setFireTicks(int) → safeSetFireTicks(Entity, int)
            if ("setFireTicks".equals(name)) {
                methodInsn.owner = PATCHER_OWNER;
                methodInsn.name = "safeSetFireTicks";
                methodInsn.desc = "(Lorg/bukkit/entity/Entity;I)V";
                methodInsn.setOpcode(INVOKESTATIC);
                methodInsn.itf = false;
                return;
            }
            // Entity.setVelocity(Vector) → safeSetVelocity(Entity, Vector)
            if ("setVelocity".equals(name)) {
                methodInsn.owner = PATCHER_OWNER;
                methodInsn.name = "safeSetVelocity";
                methodInsn.desc = "(Lorg/bukkit/entity/Entity;Lorg/bukkit/util/Vector;)V";
                methodInsn.setOpcode(INVOKESTATIC);
                methodInsn.itf = false;
                return;
            }
        }

        // LivingEntity のメソッド
        if (LIVING_ENTITY_OWNER.equals(owner)) {
            // LivingEntity.damage(double) → safeDamage(LivingEntity, double)
            if ("damage".equals(name) && argCount == 1) {
                methodInsn.owner = PATCHER_OWNER;
                methodInsn.name = "safeDamage";
                methodInsn.desc = "(Lorg/bukkit/entity/LivingEntity;D)V";
                methodInsn.setOpcode(INVOKESTATIC);
                methodInsn.itf = false;
                return;
            }
            // LivingEntity.damage(double, Entity) → safeDamage(LivingEntity, double, Entity)
            if ("damage".equals(name) && argCount == 2) {
                methodInsn.owner = PATCHER_OWNER;
                methodInsn.name = "safeDamage";
                methodInsn.desc = "(Lorg/bukkit/entity/LivingEntity;DLorg/bukkit/entity/Entity;)V";
                methodInsn.setOpcode(INVOKESTATIC);
                methodInsn.itf = false;
                return;
            }
            // LivingEntity.setHealth(double) → safeSetHealth(LivingEntity, double)
            if ("setHealth".equals(name)) {
                methodInsn.owner = PATCHER_OWNER;
                methodInsn.name = "safeSetHealth";
                methodInsn.desc = "(Lorg/bukkit/entity/LivingEntity;D)V";
                methodInsn.setOpcode(INVOKESTATIC);
                methodInsn.itf = false;
                return;
            }
            // LivingEntity.addPotionEffect(PotionEffect) → safeAddPotionEffect(LivingEntity, PotionEffect)
            if ("addPotionEffect".equals(name) && argCount == 1) {
                methodInsn.owner = PATCHER_OWNER;
                methodInsn.name = "safeAddPotionEffect";
                methodInsn.desc = "(Lorg/bukkit/entity/LivingEntity;Lorg/bukkit/potion/PotionEffect;)Z";
                methodInsn.setOpcode(INVOKESTATIC);
                methodInsn.itf = false;
                return;
            }
            // LivingEntity.addPotionEffect(PotionEffect, boolean) → safeAddPotionEffect(LivingEntity, PotionEffect, boolean)
            if ("addPotionEffect".equals(name) && argCount == 2) {
                methodInsn.owner = PATCHER_OWNER;
                methodInsn.name = "safeAddPotionEffect";
                methodInsn.desc = "(Lorg/bukkit/entity/LivingEntity;Lorg/bukkit/potion/PotionEffect;Z)Z";
                methodInsn.setOpcode(INVOKESTATIC);
                methodInsn.itf = false;
                return;
            }
        }
    }

    /**
     * メソッド記述子から引数の数を取得する。
     */
    private static int getArgumentCount(String desc) {
        int count = 0;
        int i = 1;
        while (desc.charAt(i) != ')') {
            count++;
            char c = desc.charAt(i);
            if (c == 'L') {
                i = desc.indexOf(';', i) + 1;
            } else if (c == '[') {
                i++;
                while (desc.charAt(i) == '[') {
                    i++;
                }
                if (desc.charAt(i) == 'L') {
                    i = desc.indexOf(';', i) + 1;
                } else {
                    i++;
                }
            } else {
                i++;
            }
        }
        return count;
    }
}
