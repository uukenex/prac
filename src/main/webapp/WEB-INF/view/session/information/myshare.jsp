<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>
<!DOCTYPE html>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<link rel="stylesheet" href="<%=request.getContextPath() %>/assets/css/mypage.css" />
<title>${Users.userNick }님의 공유 설정</title>
</head>
<body>

	<!-- Drop Menu Header -->
		<jsp:include page="../../nonsession/layout/dropMenu_header.jsp" />
	<!-- Menu Bar Header -->
		<jsp:include page="../../nonsession/layout/menubar_header.jsp" />
		
		
	<!-- Shared Folder body part -->
	
		<div id="page-wrapper" class="boardPage-Wrapper">
			<div id="main">
				<div class="container">
				<h2 class="mypage_mypost_title">My Page _ ${Users.userNick }님의 공유 설정</h2>
					<div class="row main-row">
					
						<div class="12u">
						
							<!-- My Shared Folders -->
							<section>
								<article class="shared_folders_view">
									<h3 class="folder_name_title">내 폴더</h3>
									<br>
										<c:forEach var="myFolder" items="${myFolderList }">
											<a href="#"
										onclick="window.open('/session/shareList?folderName=${myFolder.photoFolderName }',
										'${myFolder.photoFolderName }','width=370px, height=150px');"
										onkeypress="this.onclick()"><i class="fa fa-check-square-o" aria-hidden="true"></i> ${myFolder.photoFolderName }</a>
										<br>
										</c:forEach>
										<c:if test="${empty myFolderList }">
										<h3>내 폴더가 없습니다!!</h3>
										</c:if>
								</article>
							
							<!-- Other Shared Folders -->
								<article class="shared_folders_view">
									<h3 class="folder_name_title">공유받은 폴더</h3>
									<br>
									<c:forEach var="shareFolder" items="${sharedFolderList }">
										<a href="/session/myPhoto?userId=${shareFolder.users.userId }&folderName">
											<i class="fa fa-check-square-o" aria-hidden="true"></i> ${shareFolder.users.userNick }의 포토북</a> -
										 <a href="/session/myPhoto?userId=${shareFolder.users.userId }&folderName=${shareFolder.photoFolderName }">
												${shareFolder.photoFolderName }</a>
												<br>
									</c:forEach>
									<c:if test="${empty sharedFolderList }">
										<h3>공유받은 폴더가 없습니다!!</h3>
									</c:if>
								</article>
							</section>
						</div>
					</div>
					
					<!-- footer -->
						<jsp:include page="../../nonsession/layout/footer.jsp" />
				</div>
			</div>
		</div>
		
		
<%-- 		
	<div>
		내 폴더 리스트
		<c:forEach var="myFolder" items="${myFolderList }">
			<br>
			<a href="#"
		onclick="window.open('/session/shareList?folderName=${myFolder.photoFolderName }',
		'${myFolder.photoFolderName }','width=0, height=0');"
		onkeypress="this.onclick()"> ${myFolder.photoFolderName }</a>
			<br>
			<br>
		</c:forEach>
	</div>
	<div>
		공유받은 폴더
		<c:forEach var="shareFolder" items="${sharedFolderList }">
			<br>
			<a href="/session/myPhoto?userId=${shareFolder.users.userNick }&folderName">
				${shareFolder.users.userNick }의 포토북</a> -
	  <a href="/session/myPhoto?userId=${shareFolder.users.userNick }&folderName=${shareFolder.photoFolderName }">
				${shareFolder.photoFolderName }</a>
			<br>
			<br>
		</c:forEach>
	</div>
 --%>
</body>
</html>