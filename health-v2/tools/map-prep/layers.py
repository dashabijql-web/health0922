"""
CAD 图层清单和预览（docs/06 第三节"② 挑选图层"）。在 GDAL 的 Docker 镜像里运行，见 tools/map-prep/README.md。

输入：GeoPackage（由 DXF 转来，一张表 entities，Layer 列是 CAD 图层名）。
输出：
  overview.png          全图总览（灰色），红框是厂家四网融合系统用的图层范围（zsl_line2，作对照）
  layers/<序号>.png      每个图层一张：灰色是全图，亮青色是这一层
  layers.json           每层的实体数（按几何类型）、文字条数、文字样例、范围
  index.html            上面这些拼成的页面，勾选要用的图层后复制名单

用法：python3 layers.py all.gpkg 输出目录 [--window xmin,ymin,xmax,ymax] [--ref 参考.shp]
--window 只画这个范围（不给就用全部实体的范围）。
"""
import argparse
import html
import json
import os

import numpy as np
from osgeo import gdal, ogr

gdal.UseExceptions()

BG = (12, 20, 22)
ALL = (70, 92, 96)
HIT = (25, 230, 176)
REF = (255, 80, 80)


def blank(width, height):
    return np.zeros((height, width), dtype=np.uint8)


def rasterize(ds_path, where, extent, width, height):
    """把满足 where 的实体画成 0/1 的掩码（线 1 像素，文字是点，后面加粗）。"""
    xmin, ymin, xmax, ymax = extent
    mem = gdal.GetDriverByName('MEM').Create('', width, height, 1, gdal.GDT_Byte)
    mem.SetGeoTransform((xmin, (xmax - xmin) / width, 0, ymax, 0, -(ymax - ymin) / height))
    src = ogr.Open(ds_path)
    layer = src.GetLayerByName('entities')
    layer.SetAttributeFilter(where)
    gdal.RasterizeLayer(mem, [1], layer, burn_values=[1], options=['ALL_TOUCHED=TRUE'])
    return mem.GetRasterBand(1).ReadAsArray()


def thicken(mask, r=1):
    out = mask.copy()
    for dy in range(-r, r + 1):
        for dx in range(-r, r + 1):
            out |= np.roll(np.roll(mask, dy, axis=0), dx, axis=1)
    return out


def save_png(path, rgb):
    h, w, _ = rgb.shape
    mem = gdal.GetDriverByName('MEM').Create('', w, h, 3, gdal.GDT_Byte)
    for i in range(3):
        mem.GetRasterBand(i + 1).WriteArray(rgb[:, :, i])
    gdal.GetDriverByName('PNG').CreateCopy(path, mem)


def compose(base_mask, hit_mask=None):
    rgb = np.empty(base_mask.shape + (3,), dtype=np.uint8)
    rgb[:] = BG
    rgb[base_mask > 0] = ALL
    if hit_mask is not None:
        rgb[thicken(hit_mask) > 0] = HIT
    return rgb


def draw_rect(rgb, extent, rect):
    xmin, ymin, xmax, ymax = extent
    h, w, _ = rgb.shape
    x0 = int((rect[0] - xmin) / (xmax - xmin) * w)
    x1 = int((rect[2] - xmin) / (xmax - xmin) * w)
    y0 = int((ymax - rect[3]) / (ymax - ymin) * h)
    y1 = int((ymax - rect[1]) / (ymax - ymin) * h)
    x0, x1 = max(0, x0), min(w - 1, x1)
    y0, y1 = max(0, y0), min(h - 1, y1)
    for t in range(2):
        rgb[y0 + t, x0:x1] = REF
        rgb[y1 - t, x0:x1] = REF
        rgb[y0:y1, x0 + t] = REF
        rgb[y0:y1, x1 - t] = REF


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('gpkg')
    ap.add_argument('out')
    ap.add_argument('--window')
    ap.add_argument('--ref')
    ap.add_argument('--width', type=int, default=1200)
    args = ap.parse_args()
    os.makedirs(os.path.join(args.out, 'layers'), exist_ok=True)

    src = ogr.Open(args.gpkg)
    if args.window:
        extent = tuple(float(v) for v in args.window.split(','))
    else:
        x0, x1, y0, y1 = src.GetLayerByName('entities').GetExtent()
        extent = (x0, y0, x1, y1)
    width = args.width
    height = max(1, int(width * (extent[3] - extent[1]) / (extent[2] - extent[0])))

    # 每层统计
    rows = src.ExecuteSQL("""
        SELECT Layer, ST_GeometryType(geom) AS g, COUNT(*) AS n,
               SUM(CASE WHEN Text IS NOT NULL AND Text <> '' THEN 1 ELSE 0 END) AS texts,
               MIN(ST_MinX(geom)) AS x0, MIN(ST_MinY(geom)) AS y0, MAX(ST_MaxX(geom)) AS x1, MAX(ST_MaxY(geom)) AS y1
          FROM entities GROUP BY Layer, g""", dialect='SQLITE')
    layers = {}
    for f in rows:
        name = f.GetField('Layer') or '（无图层名）'
        d = layers.setdefault(name, {'name': name, 'types': {}, 'count': 0, 'texts': 0, 'bbox': None})
        d['types'][f.GetField('g') or 'NONE'] = f.GetField('n')
        d['count'] += f.GetField('n')
        d['texts'] += f.GetField('texts') or 0
        b = [f.GetField('x0'), f.GetField('y0'), f.GetField('x1'), f.GetField('y1')]
        if None not in b:
            d['bbox'] = b if d['bbox'] is None else [min(d['bbox'][0], b[0]), min(d['bbox'][1], b[1]),
                                                     max(d['bbox'][2], b[2]), max(d['bbox'][3], b[3])]
    src.ReleaseResultSet(rows)

    for d in layers.values():
        name_sql = d['name'].replace("'", "''")
        samples = src.ExecuteSQL(
            f"SELECT DISTINCT Text FROM entities WHERE Layer = '{name_sql}' AND Text IS NOT NULL AND Text <> '' LIMIT 8",
            dialect='SQLITE')
        d['samples'] = [s.GetField(0) for s in samples]
        src.ReleaseResultSet(samples)

    ordered = sorted(layers.values(), key=lambda d: -d['count'])
    base = rasterize(args.gpkg, None, extent, width, height)

    overview = compose(base)
    if args.ref:
        ref_ds = ogr.Open(args.ref)  # 数据源要留着引用，否则图层会随它被回收
        rx0, rx1, ry0, ry1 = ref_ds.GetLayer(0).GetExtent()
        draw_rect(overview, extent, (rx0, ry0, rx1, ry1))
    save_png(os.path.join(args.out, 'overview.png'), overview)

    for i, d in enumerate(ordered, 1):
        name_sql = d['name'].replace("'", "''")
        where = "Layer IS NULL" if d['name'] == '（无图层名）' else f"Layer = '{name_sql}'"
        hit = rasterize(args.gpkg, where, extent, width, height)
        d['index'] = i
        d['image'] = f'layers/{i:03d}.png'
        d['visible'] = int(hit.sum())
        save_png(os.path.join(args.out, d['image']), compose(base, hit))

    with open(os.path.join(args.out, 'layers.json'), 'w', encoding='utf-8') as fp:
        json.dump({'extent': extent, 'layers': ordered}, fp, ensure_ascii=False, indent=1)
    write_html(os.path.join(args.out, 'index.html'), ordered, extent)
    print(f'{len(ordered)} 个图层，{sum(d["count"] for d in ordered)} 个实体，输出到 {args.out}')


def write_html(path, ordered, extent):
    cards = []
    for d in ordered:
        types = '、'.join(f'{k} {v}' for k, v in sorted(d['types'].items(), key=lambda kv: -kv[1]))
        samples = '；'.join(html.escape(s) for s in d['samples']) or '—'
        cards.append(f"""
<label class="card">
  <input type="checkbox" value="{html.escape(d['name'])}">
  <img src="{d['image']}" loading="lazy" alt="">
  <div class="meta"><b>{d['index']}. {html.escape(d['name'])}</b>
    <div>实体 {d['count']}（{html.escape(types)}）；文字 {d['texts']}</div>
    <div class="s">文字样例：{samples}</div></div>
</label>""")
    page = f"""<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><title>CAD 图层清单</title>
<style>
body {{ margin: 0; padding: 16px; background: #0b1415; color: #d8fff5; font: 14px/1.5 sans-serif; }}
h1 {{ font-size: 20px; margin: 0 0 8px; }}
.top {{ position: sticky; top: 0; background: #0b1415; padding: 8px 0; z-index: 1; }}
.grid {{ display: grid; grid-template-columns: repeat(auto-fill, minmax(380px, 1fr)); gap: 12px; }}
.card {{ display: block; border: 1px solid #234; padding: 8px; cursor: pointer; }}
.card:has(input:checked) {{ border-color: #19e6b0; background: #0f2a26; }}
.card img {{ width: 100%; display: block; background: #0c1416; }}
.meta {{ margin-top: 6px; }} .s {{ color: #7fb8ab; font-size: 12px; }}
textarea {{ width: 100%; height: 60px; background: #062824; color: #d8fff5; border: 1px solid #19e6b0; }}
</style></head><body>
<div class="top">
<h1>CAD 图层清单（共 {len(ordered)} 层，按实体数排序）</h1>
<p>灰色是全图，亮青色是这一层。总览图：<a href="overview.png" style="color:#19e6b0">overview.png</a>（红框是厂家四网融合系统的图层范围）。
勾选要放进底图的图层，下面会列出名单，复制给我。</p>
<textarea id="out" readonly placeholder="勾选后这里出现图层名单"></textarea>
</div>
<div class="grid">{''.join(cards)}</div>
<script>
const out = document.getElementById('out');
document.addEventListener('change', () => {{
  out.value = [...document.querySelectorAll('input:checked')].map(i => i.value).join('\\n');
}});
</script>
</body></html>"""
    with open(path, 'w', encoding='utf-8') as fp:
        fp.write(page)


if __name__ == '__main__':
    main()
