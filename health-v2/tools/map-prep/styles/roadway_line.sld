<?xml version="1.0" encoding="UTF-8"?>
<!--
  巷道线（docs/06 第三节"③ 发布"）。CAD 里的颜色是给白纸用的，深色底上看不见，按图层类别上色、对照截图 14：
  煤巷类绿色、岩巷类橙色、井筒和其他（设计巷道、进尺、回采工作面）灰蓝色。图层名单见 tools/map-prep/build.py。
-->
<StyledLayerDescriptor version="1.0.0"
    xmlns="http://www.opengis.net/sld" xmlns:ogc="http://www.opengis.net/ogc"
    xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.opengis.net/sld http://schemas.opengis.net/sld/1.0.0/StyledLayerDescriptor.xsd">
  <NamedLayer>
    <Name>roadway_line</Name>
    <UserStyle>
      <Title>巷道线</Title>
      <FeatureTypeStyle>
        <Rule>
          <Name>coal</Name>
          <Title>煤巷类</Title>
          <ogc:Filter>
            <ogc:Or>
              <ogc:PropertyIsEqualTo><ogc:PropertyName>layer</ogc:PropertyName><ogc:Literal>煤巷</ogc:Literal></ogc:PropertyIsEqualTo>
              <ogc:PropertyIsEqualTo><ogc:PropertyName>layer</ogc:PropertyName><ogc:Literal>9煤煤巷</ogc:Literal></ogc:PropertyIsEqualTo>
              <ogc:PropertyIsEqualTo><ogc:PropertyName>layer</ogc:PropertyName><ogc:Literal>7煤煤巷</ogc:Literal></ogc:PropertyIsEqualTo>
              <ogc:PropertyIsEqualTo><ogc:PropertyName>layer</ogc:PropertyName><ogc:Literal>煤巷ZSLK0900DCJ</ogc:Literal></ogc:PropertyIsEqualTo>
              <ogc:PropertyIsEqualTo><ogc:PropertyName>layer</ogc:PropertyName><ogc:Literal>煤巷ZSLK0700DCJ</ogc:Literal></ogc:PropertyIsEqualTo>
            </ogc:Or>
          </ogc:Filter>
          <LineSymbolizer>
            <Stroke>
              <CssParameter name="stroke">#62c64a</CssParameter>
              <CssParameter name="stroke-width">1.6</CssParameter>
              <CssParameter name="stroke-linecap">round</CssParameter>
              <CssParameter name="stroke-linejoin">round</CssParameter>
            </Stroke>
          </LineSymbolizer>
        </Rule>
        <Rule>
          <Name>rock</Name>
          <Title>岩巷类</Title>
          <ogc:Filter>
            <ogc:Or>
              <ogc:PropertyIsEqualTo><ogc:PropertyName>layer</ogc:PropertyName><ogc:Literal>岩巷</ogc:Literal></ogc:PropertyIsEqualTo>
              <ogc:PropertyIsEqualTo><ogc:PropertyName>layer</ogc:PropertyName><ogc:Literal>岩巷ZSLK0900DCJ</ogc:Literal></ogc:PropertyIsEqualTo>
            </ogc:Or>
          </ogc:Filter>
          <LineSymbolizer>
            <Stroke>
              <CssParameter name="stroke">#d9963f</CssParameter>
              <CssParameter name="stroke-width">1.6</CssParameter>
              <CssParameter name="stroke-linecap">round</CssParameter>
              <CssParameter name="stroke-linejoin">round</CssParameter>
            </Stroke>
          </LineSymbolizer>
        </Rule>
        <Rule>
          <Name>other</Name>
          <Title>井筒等</Title>
          <ElseFilter/>
          <LineSymbolizer>
            <Stroke>
              <CssParameter name="stroke">#8fb0c6</CssParameter>
              <CssParameter name="stroke-width">1.6</CssParameter>
              <CssParameter name="stroke-linecap">round</CssParameter>
              <CssParameter name="stroke-linejoin">round</CssParameter>
            </Stroke>
          </LineSymbolizer>
        </Rule>
      </FeatureTypeStyle>
    </UserStyle>
  </NamedLayer>
</StyledLayerDescriptor>
