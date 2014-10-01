<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
	<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1"/>
	<title>Notes</title>
</head>
<body>
	<g:if test="${ type == 'md' }">
		<div class="markdown">
			<markdown:renderHtml>${content}</markdown:renderHtml>
		</div>
	</g:if>
</body>
</html>