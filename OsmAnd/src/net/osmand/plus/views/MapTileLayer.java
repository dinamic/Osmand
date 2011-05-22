package net.osmand.plus.views;

import net.osmand.map.ITileSource;
import net.osmand.osm.MapUtils;
import net.osmand.plus.OsmandSettings;
import net.osmand.plus.ResourceManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Paint.Style;
import android.util.FloatMath;

public class MapTileLayer implements OsmandMapLayer {

	protected final int emptyTileDivisor = 16;
	public static final int OVERZOOM_IN = 2;
	
	private ITileSource map = null;
	
	Paint paintGrayFill;
	Paint paintBlackFill;
	Paint paintWhiteFill;
	Paint paintBitmap;
	
	protected RectF tilesRect = new RectF();
	protected RectF latlonRect = new RectF();
	protected RectF bitmapToDraw = new RectF();
	protected Rect bitmapToZoom = new Rect();

	private OsmandMapTileView view;
	private ResourceManager resourceManager;
	private OsmandSettings settings;
	private boolean visible = true;
	
	@Override
	public boolean drawInScreenPixels() {
		return false;
	}

	@Override
	public void initLayer(OsmandMapTileView view) {
		this.view = view;
		settings = view.getSettings();
		resourceManager = view.getApplication().getResourceManager();
		
		paintGrayFill = new Paint();
		paintGrayFill.setColor(Color.GRAY);
		paintGrayFill.setStyle(Style.FILL);
		// when map rotate
		paintGrayFill.setAntiAlias(true);
		
		paintBlackFill= new Paint();
		paintBlackFill.setColor(Color.BLACK);
		paintBlackFill.setStyle(Style.FILL);
		// when map rotate
		paintBlackFill.setAntiAlias(true);

		paintWhiteFill = new Paint();
		paintWhiteFill.setColor(Color.WHITE);
		paintWhiteFill.setStyle(Style.FILL);
		// when map rotate
		paintWhiteFill.setAntiAlias(true);

		paintBitmap = new Paint();
		paintBitmap.setFilterBitmap(true);
	}
	

	@Override
	public void onDraw(Canvas canvas, RectF latlonRect, RectF tilesRect, boolean nightMode) {
		if (map == null || !visible) {
			return;
		}
		ResourceManager mgr = resourceManager;
		int nzoom = view.getZoom();
		float tileX = view.getXTile();
		float tileY = view.getYTile();
		float w = view.getCenterPointX();
		float h = view.getCenterPointY();
		float ftileSize = view.getTileSize();

		int left = (int) FloatMath.floor(tilesRect.left);
		int top = (int) FloatMath.floor(tilesRect.top);
		int width = (int) FloatMath.ceil(tilesRect.right - left);
		int height = (int) FloatMath.ceil(tilesRect.bottom - top);

		boolean useInternet = settings.USE_INTERNET_TO_DOWNLOAD_TILES.get()
					&& settings.isInternetConnectionAvailable() && map.couldBeDownloadedFromInternet();
		int maxLevel = Math.min(view.getSettings().MAX_LEVEL_TO_DOWNLOAD_TILE.get(), map.getMaximumZoomSupported());
		int tileSize = map.getTileSize();

		for (int i = 0; i < width; i++) {
			for (int j = 0; j < height; j++) {
				int leftPlusI = (int) FloatMath.floor((float) MapUtils
						.getTileNumberX(nzoom, MapUtils.getLongitudeFromTile(nzoom, left + i)));
				int topPlusJ = (int) FloatMath.floor((float) MapUtils.getTileNumberY(nzoom, MapUtils.getLatitudeFromTile(nzoom, top + j)));
				float x1 = (left + i - tileX) * ftileSize + w;
				float y1 = (top + j - tileY) * ftileSize + h;
				String ordImgTile = mgr.calculateTileId(map, leftPlusI, topPlusJ, nzoom);
				// asking tile image async
				boolean imgExist = mgr.tileExistOnFileSystem(ordImgTile, map, leftPlusI, topPlusJ, nzoom);
				Bitmap bmp = null;
				boolean originalBeLoaded = useInternet && nzoom <= maxLevel;
				if (imgExist || originalBeLoaded) {
					bmp = mgr.getTileImageForMapAsync(ordImgTile, map, leftPlusI, topPlusJ, nzoom, useInternet);
				}
				if (bmp == null) {
					int div = 2;
					// asking if there is small version of the map (in cache)
					String imgTile2 = mgr.calculateTileId(map, leftPlusI / 2, topPlusJ / 2, nzoom - 1);
					String imgTile4 = mgr.calculateTileId(map, leftPlusI / 4, topPlusJ / 4, nzoom - 2);
					if (originalBeLoaded || imgExist) {
						bmp = mgr.getTileImageFromCache(imgTile2);
						div = 2;
						if (bmp == null) {
							bmp = mgr.getTileImageFromCache(imgTile4);
							div = 4;
						}
					}
					if (!originalBeLoaded && !imgExist) {
						if (mgr.tileExistOnFileSystem(imgTile2, map, leftPlusI / 2, topPlusJ / 2, nzoom - 1)
								|| (useInternet && nzoom - 1 <= maxLevel)) {
							bmp = mgr.getTileImageForMapAsync(imgTile2, map, leftPlusI / 2, topPlusJ / 2, nzoom - 1, useInternet);
							div = 2;
						} else if (mgr.tileExistOnFileSystem(imgTile4, map, leftPlusI / 4, topPlusJ / 4, nzoom - 2)
								|| (useInternet && nzoom - 2 <= maxLevel)) {
							bmp = mgr.getTileImageForMapAsync(imgTile4, map, leftPlusI / 4, topPlusJ / 4, nzoom - 2, useInternet);
							div = 4;
						}
					}

					if (bmp != null) {
						int xZoom = ((left + i) % div) * tileSize / div;
						int yZoom = ((top + j) % div) * tileSize / div;
						bitmapToZoom.set(xZoom, yZoom, xZoom + tileSize / div, yZoom + tileSize / div);
						bitmapToDraw.set(x1, y1, x1 + ftileSize, y1 + ftileSize);
						canvas.drawBitmap(bmp, bitmapToZoom, bitmapToDraw, paintBitmap);
					}
				} else {
					bitmapToZoom.set(0, 0, tileSize, tileSize);
					bitmapToDraw.set(x1, y1, x1 + ftileSize, y1 + ftileSize);
					canvas.drawBitmap(bmp, bitmapToZoom, bitmapToDraw, paintBitmap);
				}
			}
		}

	}
	
	public int getSourceTileSize() {
		return map == null ? 256 : map.getTileSize();
	}
	
	
	public int getMaximumShownMapZoom(){
		if(map == null){
			return 20;
		} else {
			return map.getMaximumZoomSupported() + OVERZOOM_IN;
		}
	}
	
	public int getMinimumShownMapZoom(){
		if(map == null){
			return 1;
		} else {
			return map.getMinimumZoomSupported();
		}
	}
		
	@Override
	public void destroyLayer() {
		// TODO clear map cache
	}

	public boolean isVisible() {
		return visible;
	}
	
	public void setVisible(boolean visible) {
		this.visible = visible;
		// TODO clear map cache
	}
	
	public ITileSource getMap() {
		return map;
	}
	
	public void setMap(ITileSource map) {
		this.map = map;
	}

	@Override
	public boolean onLongPressEvent(PointF point) {
		return false;
	}

	@Override
	public boolean onTouchEvent(PointF point) {
		return false;
	}

}
