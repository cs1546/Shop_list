# ShopList - 쇼핑 체크리스트 앱

## 프로젝트 개요
매장 방문 시 사용하는 간단한 쇼핑 체크리스트 Android 앱.

## 기술 스택
- Kotlin 2.1.10, Jetpack Compose (BOM 2025.02.00), Material 3
- Room 2.7.0 + KSP, MVVM 패턴
- AGP 8.9.0, Gradle 8.12, compileSdk 35, minSdk 26

## 아키텍처
```
[Composable UI] → [ShoppingViewModel] → [ShoppingRepository] → [ShoppingDao] → [Room/SQLite]
```
단일 Activity, 단일 화면. DI 프레임워크 없이 수동 의존성 주입.

## 코딩 스타일
- Kotlin 표준 (K&R brace, camelCase)
- Room column명만 snake_case (`is_checked`, `created_at`)
- `if()`/`for()` 공백 없이 붙여쓰기

## 핵심 기능
1. 항목 추가/편집/삭제 (Swipe-to-delete)
2. 체크 시 취소선 + 하단 정렬 (SQL ORDER BY)
3. 쿠팡/SSG 가격비교 검색 (브라우저 Intent)
4. Room DB를 통한 영구 저장
