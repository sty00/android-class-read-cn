/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.graphics.drawable;

import android.graphics.*;
import android.graphics.drawable.shapes.Shape;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * @translator AlexTam
 *	一个绘制原始形状(shapes)的Drawable对象.一个ShapeDrawable在屏幕获取一个相应的对象并管理它的存在.如果
 *	没有提供的shape,那么ShapeDrawable会默认一个RectShape(矩形shape).
 *
 * A Drawable object that draws primitive shapes. 
 * A ShapeDrawable takes a {@link android.graphics.drawable.shapes.Shape}
 * object and manages its presence on the screen. If no Shape is given, then
 * the ShapeDrawable will default to a 
 * {@link android.graphics.drawable.shapes.RectShape}.
 *	
 *	这个对象能在XML文件里通过<shape>标签去定义 - 就是说<shape>标签里定义的就是这个ShapeDrawable
 * <p>This object can be defined in an XML file with the <code><shape></code> element.</p>
 *
 * <div class="special reference">
 * <h3>Developer Guides</h3>
 *
 *	要知道更多关于如何使用ShapeDrawable的信息,可以去这个链接
 *	{@docRoot}guide/topics/graphics/2d-graphics.html#shape-drawable 阅读.
 *
 * <p>For more information about how to use ShapeDrawable, read the
 * <a href="{@docRoot}guide/topics/graphics/2d-graphics.html#shape-drawable">
 * Canvas and Drawables</a> document. For more information about defining a ShapeDrawable in
 * XML, read the
 * <a href="{@docRoot}guide/topics/resources/drawable-resource.html#Shape">Drawable Resources</a>
 * document.</p></div>
 *
 * @attr ref android.R.styleable#ShapeDrawablePadding_left
 * @attr ref android.R.styleable#ShapeDrawablePadding_top
 * @attr ref android.R.styleable#ShapeDrawablePadding_right
 * @attr ref android.R.styleable#ShapeDrawablePadding_bottom
 * @attr ref android.R.styleable#ShapeDrawable_color
 * @attr ref android.R.styleable#ShapeDrawable_width
 * @attr ref android.R.styleable#ShapeDrawable_height
 */
public class ShapeDrawable extends Drawable {
    private ShapeState mShapeState;
    private boolean mMutated;

    /**
     * ShapeDrawable constructor.
     */
    public ShapeDrawable() {
        this((ShapeState) null);
    }
    
    /**
	 * 传入具体的Shape可以创建一个ShapeDrawable对象.
	 * 比如我们在XML文件中通过shape标签定义的draw资源,其实就是这个ShapeDrawable,
	 * 而在代码中,我们可以通过诸如
	 * new ShapeDrawable(new RectShape()); //矩形shape
	 * new ShapeDrawable(new OvalShape()); //椭圆shape
	 * 这些方法去创建一个ShapeDrawable对象
	 *
     * Creates a ShapeDrawable with a specified Shape.
     * 
     * @param s the Shape that this ShapeDrawable should be
     */
    public ShapeDrawable(Shape s) {
        this((ShapeState) null);
        
        mShapeState.mShape = s;
    }
    
	//上面的构造方法都指向这个私有方法去创建ShapeDrawable.
	//从下面的方法看到,ShapeState是包含了很多Drawable状态信息(比如绘制当前Drawable对象的paint,宽高值等等)的对象
	//ShapeState是个静态类,最后面会提到.
    private ShapeDrawable(ShapeState state) {
        mShapeState = new ShapeState(state);
    }

    /**
	 * getShape()方法获取ShapeDrawable的形状(Shape是rect,oval等等)
     * Returns the Shape of this ShapeDrawable.
     */
    public Shape getShape() {
        return mShapeState.mShape;
    }
    
    /**
	 * 设置shape
     * Sets the Shape of this ShapeDrawable.
     */
    public void setShape(Shape s) {
        mShapeState.mShape = s;
		//更新shape
        updateShape();
    }
    
    /**
     * Sets a ShaderFactory to which requests for a 
     * {@link android.graphics.Shader} object will be made.
     * 
     * @param fact an instance of your ShaderFactory implementation
     */
    public void setShaderFactory(ShaderFactory fact) {
        mShapeState.mShaderFactory = fact;
    }
    
    /**
	 * ShaderFactory,这是个抽象的工厂类,每次drawable被调整大小的时候,
	 * 都会回调ShaderFactory的抽象方法abstract Shader resize(int width, int height).
	 * 比如上面的setShape()方法就会令drawable重新去绘制图形并调整大小,于是就会回调这个resize()方法.
	 * 也可以看出每一个Drawable对象(当然ShapeDrawable也是Drawable的子类)都应该有自己的ShaderFactory.
	 *
     * Returns the ShaderFactory used by this ShapeDrawable for requesting a 
     * {@link android.graphics.Shader}.
     */
    public ShaderFactory getShaderFactory() {
        return mShapeState.mShaderFactory;
    }

    /**
	 * 获取用于绘制shape的Paint对象.
     * Returns the Paint used to draw the shape.
     */
    public Paint getPaint() {
        return mShapeState.mPaint;
    }
    
    /**
	 * 传入left, top, right, bottom 四个参数去依次设置shape的Padding属性
     * Sets padding for the shape.
     * 
     * @param left    padding for the left side (in pixels)
     * @param top     padding for the top (in pixels)
     * @param right   padding for the right side (in pixels)
     * @param bottom  padding for the bottom (in pixels)
     */
    public void setPadding(int left, int top, int right, int bottom) {
        if ((left | top | right | bottom) == 0) {
            mShapeState.mPadding = null;
        } else {
            if (mShapeState.mPadding == null) {
                mShapeState.mPadding = new Rect();
            }
            mShapeState.mPadding.set(left, top, right, bottom);
        }
		//重要的绘制刷新方法,下面会提到.
        invalidateSelf();
    }
    
    /**
     * Sets padding for this shape, defined by a Rect object.
     * Define the padding in the Rect object as: left, top, right, bottom.
     */
    public void setPadding(Rect padding) {
        if (padding == null) {
            mShapeState.mPadding = null;
        } else {
            if (mShapeState.mPadding == null) {
                mShapeState.mPadding = new Rect();
            }
            mShapeState.mPadding.set(padding);
        }
        invalidateSelf();
    }
    
    /**
	 * 设置默认的宽度
     * Sets the intrinsic (default) width for this shape.
     * 
     * @param width the intrinsic width (in pixels)
     */
    public void setIntrinsicWidth(int width) {
        mShapeState.mIntrinsicWidth = width;
        invalidateSelf();
    }
    
    /**
	 * 设置默认的高度
     * Sets the intrinsic (default) height for this shape.
     * 
     * @param height the intrinsic height (in pixels)
     */
    public void setIntrinsicHeight(int height) {
        mShapeState.mIntrinsicHeight = height;
        invalidateSelf();
    }
    
	
    @Override
    public int getIntrinsicWidth() {
        return mShapeState.mIntrinsicWidth;
    }
    
    @Override
    public int getIntrinsicHeight() {
        return mShapeState.mIntrinsicHeight;
    }
    
    @Override
    public boolean getPadding(Rect padding) {
        if (mShapeState.mPadding != null) {
            padding.set(mShapeState.mPadding);
            return true;
        } else {
            return super.getPadding(padding);
        }
    }

	//调整Alpha值的私有方法,不重要
    private static int modulateAlpha(int paintAlpha, int alpha) {
        int scale = alpha + (alpha >>> 7);  // convert to 0..256
        return paintAlpha * scale >>> 8;
    }

    /**
	 * 画布被准备好,会在Drawable的draw()中回调这个方法.任何Drawable的子类都可以覆盖重写这个方法.
	 * 
     * Called from the drawable's draw() method after the canvas has been set
     * to draw the shape at (0,0). Subclasses can override for special effects
     * such as multiple layers, stroking, etc.
     */
    protected void onDraw(Shape shape, Canvas canvas, Paint paint) {
        shape.draw(canvas, paint);
    }

	//绘画方法
    @Override
    public void draw(Canvas canvas) {
		//获取大小,画笔,透明度的信息
        Rect r = getBounds();
        Paint paint = mShapeState.mPaint;

        int prevAlpha = paint.getAlpha();
        paint.setAlpha(modulateAlpha(prevAlpha, mShapeState.mAlpha));

        if (mShapeState.mShape != null) {
            // need the save both for the translate, and for the (unknown) Shape
			//这里涉及底层绘画的原理方法.canvas.restoreToCount()被调用前,必须先使用canvas.save()去保存画布的状态,不然会报错.
            int count = canvas.save();
            canvas.translate(r.left, r.top);
            onDraw(mShapeState.mShape, canvas, paint);
			//原句是now the canvas is back in the same state it was before the initial. 
			//画布被重置到初始化前的状态.等于可以重新在画布上去绘画新的东西
            canvas.restoreToCount(count);
        } else {
			//上面提到如果创建ShapeDrawable传入的参数为null,就默认创建矩形的ShapeDrawable
			//这个方法就是默认处理绘画矩形的实现
            canvas.drawRect(r, paint);
        }
        
        // restore
        paint.setAlpha(prevAlpha);
    }

	//当Drawable发生变化时 ,调用该方法返回Drawable的configuration参数
    @Override
    public int getChangingConfigurations() {
        return super.getChangingConfigurations()
                | mShapeState.mChangingConfigurations;
    }
    
    /**
	 * 设置alpha的大小,这里会结合color的alpha值和drawable的alpha值去的到一个新的alpha值,
	 * 这个新的alpha值才是真正的绘画时用到的alpha值.
	 * 
     * Set the alpha level for this drawable [0..255]. Note that this drawable
     * also has a color in its paint, which has an alpha as well. These two
     * values are automatically combined during drawing. Thus if the color's
     * alpha is 75% (i.e. 192) and the drawable's alpha is 50% (i.e. 128), then
     * the combined alpha that will be used during drawing will be 37.5%
     * (i.e. 96).
     */
    @Override public void setAlpha(int alpha) {
        mShapeState.mAlpha = alpha;
        invalidateSelf();
    }
    
	//设置ColorFilter.
    @Override
    public void setColorFilter(ColorFilter cf) {
        mShapeState.mPaint.setColorFilter(cf);
        invalidateSelf();
    }
    
	//获取透明度
    @Override
    public int getOpacity() {
        if (mShapeState.mShape == null) {
            final Paint p = mShapeState.mPaint;
            if (p.getXfermode() == null) {
                final int alpha = p.getAlpha();
                if (alpha == 0) {
                    return PixelFormat.TRANSPARENT;
                }
                if (alpha == 255) {
                    return PixelFormat.OPAQUE;
                }
            }
        }
        // not sure, so be safe
        return PixelFormat.TRANSLUCENT;
    }

	//给Paint设置flag属性,比如常用的paint.setFlags(Paint.ANTI_ALIAS_FLAG); 用于去掉锯齿.
	//dither为true - dither会被设入, 为false则清除paint的flags.
    @Override
    public void setDither(boolean dither) {
        mShapeState.mPaint.setDither(dither);
        invalidateSelf();
    }

	//覆盖drawable的同名方法,当确定drawable的子类重绘时调用该方法
    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        updateShape();
    }

    /**
	 * 子类覆盖这个方法去实现对XML文件中标签的分析,如果确定去实现它,就返回true.否则就返回super.inflateTag(...)
	 * 一般不建议自己去写.好吧,除非你是有特别需求.
	 *
     * Subclasses override this to parse custom subelements.
     * If you handle it, return true, else return <em>super.inflateTag(...)</em>.
     */
    protected boolean inflateTag(String name, Resources r, XmlPullParser parser,
            AttributeSet attrs) {

        if ("padding".equals(name)) {
            TypedArray a = r.obtainAttributes(attrs,
                    com.android.internal.R.styleable.ShapeDrawablePadding);
            setPadding(
                    a.getDimensionPixelOffset(
                            com.android.internal.R.styleable.ShapeDrawablePadding_left, 0),
                    a.getDimensionPixelOffset(
                            com.android.internal.R.styleable.ShapeDrawablePadding_top, 0),
                    a.getDimensionPixelOffset(
                            com.android.internal.R.styleable.ShapeDrawablePadding_right, 0),
                    a.getDimensionPixelOffset(
                            com.android.internal.R.styleable.ShapeDrawablePadding_bottom, 0));
            a.recycle();
            return true;
        }

        return false;
    }

	//覆盖重写了父类的方法,解析XML文件,读取XML的各种参数...
    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs)
                        throws XmlPullParserException, IOException {
        super.inflate(r, parser, attrs);

        TypedArray a = r.obtainAttributes(attrs, com.android.internal.R.styleable.ShapeDrawable);

        int color = mShapeState.mPaint.getColor();
        color = a.getColor(com.android.internal.R.styleable.ShapeDrawable_color, color);
        mShapeState.mPaint.setColor(color);

        boolean dither = a.getBoolean(com.android.internal.R.styleable.ShapeDrawable_dither, false);
        mShapeState.mPaint.setDither(dither);

        setIntrinsicWidth((int)
                a.getDimension(com.android.internal.R.styleable.ShapeDrawable_width, 0f));
        setIntrinsicHeight((int)
                a.getDimension(com.android.internal.R.styleable.ShapeDrawable_height, 0f));

        a.recycle();

        int type;
        final int outerDepth = parser.getDepth();
        while ((type=parser.next()) != XmlPullParser.END_DOCUMENT
               && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type != XmlPullParser.START_TAG) {
                continue;
            }
            
            final String name = parser.getName();
            // call our subclass
            if (!inflateTag(name, r, parser, attrs)) {
                android.util.Log.w("drawable", "Unknown element: " + name +
                        " for ShapeDrawable " + this);
            }
        }
    }
	
	//每次设置shape后都要去绘制并更新
    private void updateShape() {
        if (mShapeState.mShape != null) {
			//获取宽高,重新调整shape的大小
            final Rect r = getBounds();
            final int w = r.width();
            final int h = r.height();

            mShapeState.mShape.resize(w, h);
            if (mShapeState.mShaderFactory != null) {
                mShapeState.mPaint.setShader(mShapeState.mShaderFactory.resize(w, h));
            }
        }
		//这个方法比较重要,需要实现Callback接口里面的invalidateDrawable(Drawable who),
		//scheduleDrawable(Drawable who, Runnable what, long when)和unscheduleDrawable(Drawable who, Runnable what)方法.
		//如果接口为null,那么就不会回调Callback.invalidateDrawable()方法,也就无法完成重新绘制更新的动作.Drawable的刷新就会失败.
		//上面的很多方法,比如setPadding(),setShape()等等都调用invalidateSelf()去实现,呈现最终的Drawable效果.可见这个方法有多关键哦.
        invalidateSelf();
    }
    
	//获取ConstantState对象.它保存了一些和其他Drawable分享共用的常量状态和数据.
	//为什么会有共用常量状态和数据的情况? 比如当开发者从相同的资源中去创建Drawable的时候,这些Drawable之间就能分享共用它们的状态了.
    @Override
    public ConstantState getConstantState() {
        mShapeState.mChangingConfigurations = getChangingConfigurations();
        return mShapeState;
    }

	//这个方法是如果Drawable确定要变化的时候,会设入一个mutate的值,这个时候当前的Drawable子类就不会再和其他Drawable分享状态参数.
	//这个方法比较少用到的,不必太关心.
    @Override
    public Drawable mutate() {
        if (!mMutated && super.mutate() == this) {
            mShapeState.mPaint = new Paint(mShapeState.mPaint);
            mShapeState.mPadding = new Rect(mShapeState.mPadding);
            try {
                mShapeState.mShape = mShapeState.mShape.clone();
            } catch (CloneNotSupportedException e) {
                return null;
            }
            mMutated = true;
        }
        return this;
    }

    /**
	 * 继承了ConstantState的静态类
	 * 给特定ShapeDrawable的shape定义默认的属性,这里验证了最初的想法,ShapeState包含了当前Drawable的重要属性.
	 * ShapeState是Drawable自己的保存状态量和数据的重要对象.
     * Defines the intrinsic properties of this ShapeDrawable's Shape.
     */
    final static class ShapeState extends ConstantState {
        int mChangingConfigurations;
        Paint mPaint;
        Shape mShape;
        Rect mPadding;
        int mIntrinsicWidth;
        int mIntrinsicHeight;
        int mAlpha = 255;
        ShaderFactory mShaderFactory;
        
        ShapeState(ShapeState orig) {
            if (orig != null) {
                mPaint = orig.mPaint;
                mShape = orig.mShape;
                mPadding = orig.mPadding;
                mIntrinsicWidth = orig.mIntrinsicWidth;
                mIntrinsicHeight = orig.mIntrinsicHeight;
                mAlpha = orig.mAlpha;
                mShaderFactory = orig.mShaderFactory;
            } else {
                mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            }
        }
        
        @Override
        public Drawable newDrawable() {
            return new ShapeDrawable(this);
        }
        
        @Override
        public Drawable newDrawable(Resources res) {
            return new ShapeDrawable(this);
        }
        
        @Override
        public int getChangingConfigurations() {
            return mChangingConfigurations;
        }
    }
    
    /**
	 * (上面开始的时候分析过这个类,这里就不在重复了.)
     * Base class defines a factory object that is called each time the drawable
     * is resized (has a new width or height). Its resize() method returns a
     * corresponding shader, or null.
     * Implement this class if you'd like your ShapeDrawable to use a special
     * {@link android.graphics.Shader}, such as a 
     * {@link android.graphics.LinearGradient}. 
     * 
     */
    public static abstract class ShaderFactory {
        /**
         * Returns the Shader to be drawn when a Drawable is drawn.
         * The dimensions of the Drawable are passed because they may be needed
         * to adjust how the Shader is configured for drawing.
         * This is called by ShapeDrawable.setShape().
         * 
         * @param width  the width of the Drawable being drawn
         * @param height the heigh of the Drawable being drawn
         * @return       the Shader to be drawn
         */
        public abstract Shader resize(int width, int height);
    }
    
    // other subclass could wack the Shader's localmatrix based on the
    // resize params (e.g. scaletofit, etc.). This could be used to scale
    // a bitmap to fill the bounds without needing any other special casing.
}
