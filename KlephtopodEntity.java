package net.jaiz.jaizmobs.entity.custom;

import com.google.common.collect.Sets;
import net.jaiz.jaizmobs.entity.ai.KlephtopodAttackGoal;
import net.jaiz.jaizmobs.entity.ai.TotemSpiritAttackGoal;
import net.minecraft.block.BlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.control.AquaticMoveControl;
import net.minecraft.entity.ai.control.YawAdjustingLookControl;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.SwimNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.WaterCreatureEntity;
import net.minecraft.entity.passive.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;

public class KlephtopodEntity extends WaterCreatureEntity implements ItemSteerable{

    private static final Ingredient ATTRACTING_INGREDIENT = Ingredient.ofItems(Items.NAUTILUS_SHELL, Items.TROPICAL_FISH);
    @Nullable
    private TemptGoal temptGoal;

    private static final TrackedData<Boolean> ATTACKING =
            DataTracker.registerData(KlephtopodEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    public AnimationState idleAnimationState = new AnimationState();
    private int idleAnimationTimeout = 0;

    public AnimationState attackAnimationState = new AnimationState();
    public int attackAnimationTimeout = 0;


    public KlephtopodEntity(EntityType<? extends WaterCreatureEntity > entityType, World world) {

        super(entityType, world);
        this.moveControl = new AquaticMoveControl(this, 85, 10, 0.022f, 0.1f, true);
        this.lookControl = new YawAdjustingLookControl(this, 10);
        this.experiencePoints = 20;
    }

    private void setupAnimationStates() {
        if (this.idleAnimationTimeout <= 0) {
            this.idleAnimationTimeout = this.random.nextInt(40) + 80;
            this.idleAnimationState.start(this.age);
        } else {
            --this.idleAnimationTimeout;
        }

        if(this.isAttacking()  && attackAnimationTimeout <= 0) {
            attackAnimationTimeout = 20;
            attackAnimationState.start(this.age);
        } else {
            --this.attackAnimationTimeout;
        }

        if(!this.isAttacking()) {
            attackAnimationState.stop();
        }

    }




    @Override
    protected void updateLimbs(float posDelta) {
        float f = this.getPose() == EntityPose.STANDING ? Math.min(posDelta * 6.0f, 1.0f) : 0.0f;
        this.limbAnimator.updateLimbs(f, 0.2f);
    }

    @Override
    public void tick() {
        super.tick();
        if(this.getWorld().isClient()) {
            setupAnimationStates();
        }
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(8, new LookAroundGoal(this));
        this.initCustomGoals();
    }

    protected void initCustomGoals() {
        this.goalSelector.add(0, new MoveIntoWaterGoal(this));
        this.goalSelector.add(4, new SwimAroundGoal(this, 1.0, 10));
        this.goalSelector.add(1, new KlephtopodAttackGoal(this, 1d, false));
        this.targetSelector.add(4, new RevengeGoal(this));
        this.temptGoal = new TemptGoal(this, 1.4, ATTRACTING_INGREDIENT, false);
        this.goalSelector.add(3, this.temptGoal);
        this.goalSelector.add(8, new ChaseBoatGoal(this));
        this.targetSelector.add(5, new RevengeGoal(this));

    }

    public static DefaultAttributeContainer.Builder createKlephtopodAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 1.2f)
                .add(EntityAttributes.GENERIC_ARMOR, 2.6f)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40);

    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        return new SwimNavigation(this, world);
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!player.shouldCancelInteraction()) {
            if (!this.getWorld().isClient) {
                player.startRiding(this);
            }
            return ActionResult.success(this.getWorld().isClient);
        }
        ActionResult actionResult = super.interactMob(player, hand);

        if (!this.isSilent()) {
            this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_DOLPHIN_EAT, this.getSoundCategory(), 1.0f, 1.0f + (this.random.nextFloat() - this.random.nextFloat()) * 0.2f);
        }
        return actionResult;
    }

    public void setAttacking(boolean attacking) {
        this.dataTracker.set(ATTACKING, attacking);
    }

    @Override
    public boolean isAttacking() {
        return this.dataTracker.get(ATTACKING);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(ATTACKING, false);
    }



    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_GLOW_SQUID_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_TURTLE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_TURTLE_DEATH;
    }

    @Override
    protected SoundEvent getSplashSound() {
        return SoundEvents.ENTITY_DOLPHIN_SPLASH;
    }

    @Override
    protected SoundEvent getSwimSound() {
        return SoundEvents.ENTITY_DOLPHIN_SWIM;
    }

    @Override
    public void travel(Vec3d movementInput) {
        if (this.canMoveVoluntarily() && this.isTouchingWater()) {
            this.updateVelocity(this.getMovementSpeed(), movementInput);
            this.move(MovementType.SELF, this.getVelocity());
            this.setVelocity(this.getVelocity().multiply(0.9));
            if (this.getTarget() == null) {
                this.setVelocity(this.getVelocity().add(0.0, -0.005, 0.0));
            }
        } else {
            super.travel(movementInput);
        }
    }

    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        PlayerEntity playerEntity;
        Entity entity;
        if ((entity = this.getFirstPassenger()) instanceof PlayerEntity && (playerEntity = (PlayerEntity)entity).isHolding(Items.NAUTILUS_SHELL)) {
            return playerEntity;
        }
        return super.getControllingPassenger();
    }

    @Override
    public Vec3d updatePassengerForDismount(LivingEntity passenger) {
        Vec3d[] vec3ds = new Vec3d[]{KlephtopodEntity.getPassengerDismountOffset(this.getWidth(), passenger.getWidth(), passenger.getYaw()), StriderEntity.getPassengerDismountOffset(this.getWidth(), passenger.getWidth(), passenger.getYaw() - 22.5f), KlephtopodEntity.getPassengerDismountOffset(this.getWidth(), passenger.getWidth(), passenger.getYaw() + 22.5f), KlephtopodEntity.getPassengerDismountOffset(this.getWidth(), passenger.getWidth(), passenger.getYaw() - 45.0f), KlephtopodEntity.getPassengerDismountOffset(this.getWidth(), passenger.getWidth(), passenger.getYaw() + 45.0f)};
        LinkedHashSet<BlockPos> set = Sets.newLinkedHashSet();
        double d = this.getBoundingBox().maxY;
        double e = this.getBoundingBox().minY - 0.5;
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (Vec3d vec3d : vec3ds) {
            mutable.set(this.getX() + vec3d.x, d, this.getZ() + vec3d.z);
            for (double f = d; f > e; f -= 1.0) {
                set.add(mutable.toImmutable());
                mutable.move(Direction.DOWN);
            }
        }
        for (BlockPos blockPos : set) {
            double g;
            if (this.getWorld().getFluidState(blockPos).isIn(FluidTags.LAVA) || !Dismounting.canDismountInBlock(g = this.getWorld().getDismountHeight(blockPos))) continue;
            Vec3d vec3d2 = Vec3d.ofCenter(blockPos, g);
            for (EntityPose entityPose : passenger.getPoses()) {
                Box box = passenger.getBoundingBox(entityPose);
                if (!Dismounting.canPlaceEntityAt(this.getWorld(), passenger, box.offset(vec3d2))) continue;
                passenger.setPose(entityPose);
                return vec3d2;
            }
        }
        return new Vec3d(this.getX(), this.getBoundingBox().maxY, this.getZ());
    }

    @Override
    protected void tickControlled(PlayerEntity controllingPlayer, Vec3d movementInput) {
        this.setRotation(controllingPlayer.getYaw(), controllingPlayer.getPitch() * 0.5f);
        this.bodyYaw = this.headYaw = this.getYaw();
        this.prevYaw = this.headYaw;
        super.tickControlled(controllingPlayer, movementInput);
    }

    @Override
    protected Vec3d getControlledMovementInput(PlayerEntity controllingPlayer, Vec3d movementInput) {
        return new Vec3d(0.0, 0.0, 1.0);
    }


    @Override
    public boolean canBeLeashedBy(PlayerEntity player) {
        return true;
    }


    @Override
    public boolean consumeOnAStickItem() {
        return false;
    }
}
