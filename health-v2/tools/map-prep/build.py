"""
把挑好的 CAD 图层做成底图数据（docs/06 第三节"② 挑选图层"）。在 GDAL 的 Docker 镜像里运行，见 README.md。

输入：mine.dxf（dwg2dxf 转来的）。直接读 DXF：颜色、字高在 DXF 的样式里，转成 GeoPackage 会丢。
输出：mine-map.gpkg，两张表，坐标已平移回真实坐标（EPSG:4527）：
  roadway_line   巷道线：layer（CAD 图层名）、color（CAD 里的颜色，#rrggbb，样式按它上色）
  roadway_label  巷道文字：layer、text、size（字高，米）、angle（度）
另外打印两张表的范围，写进地图配置用（docs/06：地图范围按选中的数据算，不用 DXF 文件头）。

为什么要平移：这张 dwg 里巷道所在的那幅图（带图框的通风系统图）整体偏离了真实坐标。厂家四网融合系统用的
巷道层就是把它平移 (-11633.321, +988.923) 米得到的，没有旋转缩放；按这个平移量，99.5% 的线和厂家的重合，
也正好落在同一张 dwg 左边地质平面图（真实坐标）的老采区上。核对过程见 docs/06 第三节。
"""
import re
import sys

from osgeo import gdal, ogr, osr

gdal.UseExceptions()
ogr.UseExceptions()

OFFSET = (-11633.321, 988.923)

LINE_LAYERS = [
    '岩巷', '煤巷', '9煤煤巷', '7煤煤巷', '井筒',
    '煤巷ZSLK0900DCJ', '岩巷ZSLK0900DCJ', '设计巷道ZSLK0900DCJ', '进尺ZSLK0900DCJ',
    '回采工作面ZSLK0900DCJ', '回采月份ZSLK0900DCJ', '煤巷ZSLK0700DCJ',
]
# 煤巷里有一条文字（西翼增补风道），一起要；合起来和厂家的文字层一样是 112 条
LABEL_LAYERS = ['巷道名称', '岩巷道名', '防尘', '井筒', '煤巷']

PEN_COLOR = re.compile(r'c:(#[0-9A-Fa-f]{6})')
LABEL_SIZE = re.compile(r's:([0-9.]+)g')
LABEL_ANGLE = re.compile(r'a:(-?[0-9.]+)')


def shift(geom):
    for i in range(geom.GetGeometryCount()):
        shift(geom.GetGeometryRef(i))
    for j in range(geom.GetPointCount()):
        x, y = geom.GetPoint_2D(j)
        geom.SetPoint_2D(j, x + OFFSET[0], y + OFFSET[1])


def in_list(names):
    return 'Layer IN (' + ','.join("'" + n.replace("'", "''") + "'" for n in names) + ')'


def main(src_path, dst_path):
    src = ogr.Open(src_path)
    entities = src.GetLayer('entities')
    srs = osr.SpatialReference()
    srs.ImportFromEPSG(4527)

    dst = ogr.GetDriverByName('GPKG').CreateDataSource(dst_path)
    lines = dst.CreateLayer('roadway_line', srs, ogr.wkbMultiLineString, ['SPATIAL_INDEX=YES'])
    lines.CreateField(ogr.FieldDefn('layer', ogr.OFTString))
    lines.CreateField(ogr.FieldDefn('color', ogr.OFTString))
    labels = dst.CreateLayer('roadway_label', srs, ogr.wkbPoint, ['SPATIAL_INDEX=YES'])
    labels.CreateField(ogr.FieldDefn('layer', ogr.OFTString))
    labels.CreateField(ogr.FieldDefn('text', ogr.OFTString))
    labels.CreateField(ogr.FieldDefn('size', ogr.OFTReal))
    labels.CreateField(ogr.FieldDefn('angle', ogr.OFTReal))

    n_lines = n_labels = 0
    entities.SetAttributeFilter(in_list(sorted(set(LINE_LAYERS) | set(LABEL_LAYERS))))
    dst.StartTransaction()
    for f in entities:
        g = f.GetGeometryRef()
        if g is None:
            continue
        layer = f.GetField('Layer')
        text = (f.GetField('Text') or '').strip()
        style = f.GetStyleString() or ''
        if text:
            if layer not in LABEL_LAYERS:
                continue
            c = g.Centroid()
            shift(c)
            out = ogr.Feature(labels.GetLayerDefn())
            out.SetField('layer', layer)
            out.SetField('text', text)
            m = LABEL_SIZE.search(style)
            out.SetField('size', float(m.group(1)) if m else None)
            m = LABEL_ANGLE.search(style)
            out.SetField('angle', float(m.group(1)) if m else 0.0)
            out.SetGeometry(c)
            labels.CreateFeature(out)
            n_labels += 1
            continue
        if layer not in LINE_LAYERS:
            continue
        flat = ogr.ForceToMultiLineString(g.Clone())
        if flat is None or flat.IsEmpty() or ogr.GT_Flatten(flat.GetGeometryType()) != ogr.wkbMultiLineString:
            continue  # 实心填充等不是线的，底图不要
        flat.FlattenTo2D()
        shift(flat)
        out = ogr.Feature(lines.GetLayerDefn())
        out.SetField('layer', layer)
        m = PEN_COLOR.search(style)
        out.SetField('color', m.group(1).lower() if m else None)
        out.SetGeometry(flat)
        lines.CreateFeature(out)
        n_lines += 1
    dst.CommitTransaction()

    for name, lyr, n in (('roadway_line', lines, n_lines), ('roadway_label', labels, n_labels)):
        x0, x1, y0, y1 = lyr.GetExtent()
        print(f'{name}: {n} 条，范围 {x0:.1f},{y0:.1f} - {x1:.1f},{y1:.1f}')
    dst = None


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])  # python3 build.py mine.dxf mine-map.gpkg
