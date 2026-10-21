# Builds the pet store's DSL guide page from the real DSL files and the real
# generated code, so no excerpt on the page can differ from the source.
import io, re, html

import os

# The sample module: this script lives in its guide/ folder. Run after a build
# (the generated sources are read), e.g.  python guide/build_guide.py
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__))).replace(os.sep, '/') + '/'
RES = ROOT + 'src/main/resources/'
JAVA = ROOT + 'src/main/java/io/elasticore/sample/petstore/'
OUT = RES + 'static/dsl-guide.html'


def read(p):
    return io.open(p, encoding='utf-8').read().replace('\r\n', '\n')


old = read(OUT)
STYLE = old[old.index('<style>'):old.index('</style>') + len('</style>')]
HIGHLIGHT = old[old.rindex('<script>'):old.rindex('</script>') + len('</script>')]

ENV = read(RES + 'blueprint/petstore/env.yml').rstrip()
ENUM = read(RES + 'blueprint/petstore/model/enum.yml').rstrip()
ENTITY = read(RES + 'blueprint/petstore/model/entity.yml')
DTO = read(RES + 'blueprint/petstore/model/dto.yml').rstrip()
METRICS = read(RES + 'blueprint/petstore/model/metrics.yml').rstrip()
SEMANTIC = read(RES + 'blueprint/semantic_model.yml')


def block(text, name, indent=2):
    """The YAML block of one entry (entity, dto, enum) by name, as written."""
    lines = text.split('\n')
    pad = ' ' * indent
    start = None
    for i, l in enumerate(lines):
        if l.rstrip() == pad + name + ':':
            start = i
            break
    assert start is not None, name
    out = [lines[start]]
    for l in lines[start + 1:]:
        if l.strip() == '':
            break
        if not l.startswith(pad + ' ') and not l.startswith(pad + '#'):
            break
        if l.startswith(pad + '# ----'):
            break
        out.append(l)
    return '\n'.join(out)


def method(path, head):
    """A member of a generated Java file, from its first line to its closing brace."""
    src = read(path).replace('\t', '    ')
    i = src.index(head)
    # back up over a Javadoc directly above it
    j = src.rfind('\n', 0, i)
    k = src.rfind('*/', 0, i)
    if k != -1 and src[k:i].strip() == '*/':
        j = src.rfind('/**', 0, k)
        j = src.rfind('\n', 0, j)
    depth = 0
    end = src.index('{', i)
    for n in range(end, len(src)):
        if src[n] == '{':
            depth += 1
        elif src[n] == '}':
            depth -= 1
            if depth == 0:
                end = n + 1
                break
    text = src[j + 1:end]
    lines = text.split('\n')
    lead = min(len(l) - len(l.lstrip()) for l in lines if l.strip())
    return '\n'.join(l[lead:] for l in lines)


def semantic_metric(name):
    s = SEMANTIC
    a = s.index('  - name: ' + name + '\n', s.index('\nmetrics:\n'))
    b = s.index('\n  - name: ', a + 5)
    return s[a:b]


def semantic_dataset(name):
    s = SEMANTIC
    a = s.index('  - name: ' + name + '\n')
    b = s.index('\n  - name: ', a + 5)
    return s[a:b]


def src(id_, text):
    # Raw text: a script element shows it as written; only a closing tag would end it early.
    return '<script type="text/plain" id="src-%s">%s</script>\n' % (id_, text.replace('</script', '<\\/script'))


def code(id_, lang='yaml'):
    return '<pre class="code" data-lang="%s" data-src="%s"></pre>' % (lang, id_)


COUNTS = {'dsl': 0, 'hand': 0, 'none': 0, 'screen': 0}


def kind(dsl_cell):
    """How a rule is said, read off its DSL cell."""
    if '<b>손으로</b>' in dsl_cell:
        return 'hand'
    if 'DSL 에 규칙 없음' in dsl_cell:
        return 'none'
    if '<b>화면이</b>' in dsl_cell:
        return 'screen'
    return 'dsl'


def rules(rows):
    for r in rows:
        COUNTS[kind(r[1])] += 1
    out = ['<table class="map"><thead><tr><th>업무 규칙</th><th>DSL</th><th>확인한 결과</th></tr></thead><tbody>']
    for r in rows:
        out.append('<tr><td>%s</td><td>%s</td><td>%s</td></tr>' % r)
    out.append('</tbody></table>')
    return '\n'.join(out)


def cov(kind):
    label = {'all': 'DSL 로 전부', 'most': '대부분 DSL', 'hand': 'DSL + 손으로', 'none': 'DSL 밖'}[kind]
    return '<span class="cov %s">%s</span>' % (kind, label)


# ---- the generated code shown on the page, read from the build's output -------
G_SHIP = method(JAVA + 'domain/model/PurchaseOrderBase.java', 'public void ship()') + '\n\n' + \
    method(JAVA + 'domain/model/PurchaseOrderBase.java', 'public boolean canShip()') + '\n\n' + \
    method(JAVA + 'domain/model/PurchaseOrderBase.java', 'private void moveStatus(')
G_OWNED = method(JAVA + 'application/PurchaseOrderCoreService.java', 'private Specification<PurchaseOrder> ownedOnly(') + '\n\n' + \
    method(JAVA + 'application/PurchaseOrderCoreService.java', 'private void accessCreate(')
G_CUSTOMER = method(JAVA + 'application/CustomerCoreService.java', 'private void accessCreate(') + '\n\n' + \
    method(JAVA + 'application/CustomerCoreService.java', 'private void accessUpdate(')
G_AUTH = method(JAVA + 'application/CustomerCoreService.java', 'public Optional<CustomerDTO> authenticate(')
G_LINE = method(JAVA + 'domain/model/OrderLineBase.java', 'public void derive(')
G_ORDER_DERIVE = method(JAVA + 'domain/model/PurchaseOrderBase.java', 'public void derive(')
G_PLACE = method(JAVA + 'domain/model/PurchaseOrder.java', 'public void place(')
G_ACCOUNT = method(JAVA + 'adapter/in/web/shop/AccountController.java', 'public CustomerDTO signUp(')
G_SIGNIN = method(JAVA + 'adapter/in/web/CustomerController.java', 'public CustomerDTO signIn(') + '\n\n' + \
    method(JAVA + 'adapter/in/web/CustomerController.java', 'public ResponseEntity<Void> signOut(') + '\n\n' + \
    method(JAVA + 'adapter/in/web/CustomerController.java', 'public CustomerDTO me(')

ORDER_BLOCK = block(ENTITY, 'PurchaseOrder')
SECTIONS = []

# ---- page ------------------------------------------------------------------------
page = []
page.append('''<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Pet Store DSL 가이드</title>
  <link rel="stylesheet" href="/style.css">
  ''' + STYLE + '''
  <style>
    .cov { display: inline-block; font-size: .72rem; font-weight: 600; padding: 2px 9px; border-radius: 99px; margin-left: 8px; vertical-align: 3px; border: 1px solid currentColor; }
    .cov.all { color: var(--ok); } .cov.most { color: var(--accent); } .cov.hand { color: var(--warn); } .cov.none { color: var(--muted); }
    .biz { background: var(--panel); border: 1px solid var(--line); border-radius: 10px; padding: 12px 16px; }
    .biz b { color: var(--ink); }
    .roles { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 12px; margin: 12px 0; }
    .role { background: var(--panel); border: 1px solid var(--line); border-radius: 10px; padding: 12px 14px; }
    .role h4 { margin: 0 0 4px; }
    .gap { border-left: 3px solid var(--warn); background: var(--panel); padding: 10px 14px; border-radius: 0 8px 8px 0; margin: 12px 0; }
    .gap b { color: var(--warn); }
    table.map td code { white-space: nowrap; }
    table.map td, table.map th { word-break: keep-all; }
    table.map td:first-child { min-width: 9.5em; }
    details { margin: 10px 0; } summary { cursor: pointer; color: var(--accent); }
    .matrix td, .matrix th { text-align: center; } .matrix td:first-child, .matrix th:first-child { text-align: left; }
  </style>
</head>
<body>
  <header class="top">
    <a class="brand" href="/">YAPS <span>Pet Store</span></a>
    <nav class="cats"><a href="/">쇼핑몰로</a><a href="/dsl-guide.html">DSL 가이드</a><a href="/swagger-ui.html">API</a></nav>
  </header>

  <div class="guide">
    <nav class="toc" aria-label="목차">
      <strong>개요</strong>
      <a href="#overview">이 가게는</a>
      <a href="#people">사람과 권한</a>
      <a href="#map">업무 지도</a>
      <strong>업무별</strong>
      <a href="#browse">1. 상품 둘러보기</a>
      <a href="#catalog-admin">2. 상품 관리</a>
      <a href="#signup">3. 회원 가입</a>
      <a href="#signin">4. 로그인·로그아웃</a>
      <a href="#profile">5. 내 정보</a>
      <a href="#cart">6. 장바구니</a>
      <a href="#checkout">7. 주문(결제)</a>
      <a href="#orders">8. 주문 조회</a>
      <a href="#cancel">9. 주문 취소</a>
      <a href="#ship">10. 배송</a>
      <strong>DSL 자세히</strong>
      <a href="#lifecycle">주문 생명주기</a>
      <a href="#metrics">업무 지표·용어</a>
      <a href="#errors">오류 응답</a>
      <a href="#hand">손으로 쓴 것</a>
      <strong>검토</strong>
      <a href="#review">DSL 로 다 표현되나</a>
      <a href="#ossie">Ossie 시맨틱 모델</a>
      <a href="#all-dsl">전체 DSL</a>
    </nav>

    <article>
      <p class="back"><a href="/">← 쇼핑몰로 돌아가기</a></p>
      <h1 id="overview">Pet Store - 업무를 DSL 로</h1>
      <div class="biz">
        <p><b>YAPS Pet Store</b> 는 애완동물을 파는 온라인 가게입니다 (Java EE 7 pet store 를 ElastiCORE 3.0 으로 다시 만든 것).
          손님은 <b>분류</b>(물고기·개·파충류·고양이·새) → <b>상품</b>(불독·푸들…) → <b>품목</b>(수컷 강아지·암컷 강아지처럼 실제로 사는 단위)
          순으로 둘러보고 장바구니에 담습니다. 결제하려면 <b>회원 가입</b>·<b>로그인</b>하고, 배송지와 카드를 넣으면 <b>주문이 접수</b>됩니다.
          금액은 품목 가격 합계에 <b>VAT 5.5%</b> 를 더하고, VAT 포함 금액에서 <b>2.25% 할인</b>합니다.
          손님은 자기 주문을 보고 <b>취소</b>할 수 있고(배송 전까지), 가게 관리자는 백오피스에서 주문을 <b>배송</b> 처리하고 상품을 관리합니다.</p>
      </div>
      <p class="lead">이 문서는 그 업무를 <b>업무 단위로</b> 나눠, 각 규칙이 DSL 의 어디에 적혔는지, 무엇이 생성되고 무엇을 손으로 썼는지,
        그리고 <b>DSL 로 아직 표현할 수 없는 것</b>까지 적습니다. "확인한 결과" 열은 실행 중인 애플리케이션에 HTTP 로 요청해서 본 응답입니다.
        DSL 과 생성 코드 발췌는 빌드 결과물에서 그대로 읽어 왔습니다.</p>
      <div class="stats">
        <div class="stat"><b>149</b><span>DSL 줄 (4개 파일)</span></div>
        <div class="stat"><b>89 · 8,316</b><span>생성된 Java 파일 · 줄</span></div>
        <div class="stat"><b>34 · 1,144</b><span>생성된 TypeScript 파일 · 줄</span></div>
        <div class="stat"><b>7 · 256</b><span>손으로 쓴 Java 파일 · 줄</span></div>
        <!-- line counts leave out blank lines; the screens (web/src, 540 lines) are hand-written too -->
      </div>

      <h2 id="people">사람과 권한</h2>
      <div class="roles">
        <div class="role"><h4>방문자</h4>로그인 전. 상품을 보고 검색하고, 가입합니다.</div>
        <div class="role"><h4>고객 (USER)</h4>가입하면 이 역할. 자기 정보와 자기 주문만 보고 고칩니다.</div>
        <div class="role"><h4>관리자 (ADMIN)</h4>모든 것을 봅니다. 배송, 상품 관리, 고객 역할 변경, 삭제.</div>
      </div>
      <p>DSL 은 엔티티마다 <code>@access(연산=수준)</code> 으로 말합니다. 수준은 <code>public</code>(방문자도) · <code>user</code>(로그인) ·
        <code>owner</code>(본인 것만, <code>@owner(필드)</code> 로 주인을 정함) · <code>admin</code>. <b>적지 않은 연산은 관리자만</b> 할 수 있습니다.</p>
      <table class="map matrix">
        <thead><tr><th>할 일</th><th>방문자</th><th>고객 (본인 것)</th><th>고객 (남의 것)</th><th>관리자</th><th>DSL</th></tr></thead>
        <tbody>
          <tr><td>상품 보기·검색</td><td>200</td><td>200</td><td>-</td><td>200</td><td><code>read=public</code></td></tr>
          <tr><td>상품 만들기·고치기·지우기</td><td>401</td><td>403</td><td>-</td><td>200</td><td>(적지 않음 → 관리자)</td></tr>
          <tr><td>회원 가입</td><td>201</td><td>-</td><td>-</td><td>-</td><td><code>create=public</code></td></tr>
          <tr><td>고객 정보 보기</td><td>401</td><td>200</td><td>404</td><td>200</td><td><code>read=owner</code></td></tr>
          <tr><td>고객 정보 고치기</td><td>401</td><td>200</td><td>404</td><td>200</td><td><code>update=owner</code></td></tr>
          <tr><td>고객 역할 바꾸기</td><td>-</td><td>무시됨</td><td>-</td><td>200</td><td><code>role … @access(admin)</code></td></tr>
          <tr><td>탈퇴(고객 삭제)</td><td>401</td><td>403</td><td>403</td><td>200</td><td>(적지 않음 → 관리자)</td></tr>
          <tr><td>주문하기</td><td>401</td><td>200</td><td>-</td><td>200</td><td><code>create=user</code></td></tr>
          <tr><td>주문 보기</td><td>401</td><td>200</td><td>404</td><td>200</td><td><code>read=owner</code></td></tr>
          <tr><td>주문 취소</td><td>401</td><td>200</td><td>404</td><td>200</td><td><code>cancel=owner</code></td></tr>
          <tr><td>배송</td><td>401</td><td>403</td><td>403</td><td>200</td><td>(적지 않음 → 관리자)</td></tr>
          <tr><td>주문 삭제</td><td>401</td><td>403</td><td>403</td><td>200</td><td>(적지 않음 → 관리자)</td></tr>
        </tbody>
      </table>
      <p>남의 것은 403 이 아니라 <b>404</b> 입니다 - 남의 주문이 있다는 사실도 알리지 않습니다. 검사는 재생성되는 서비스(<code>…CoreService</code>)에
        들어 있어서, 어느 진입점으로 와도 같습니다. 요청 본문 형식 검사(400)가 먼저, 그다음 로그인(401)·권한(403/404) 순입니다.</p>

      <h2 id="map">업무 지도</h2>
      <table class="map">
        <thead><tr><th>업무</th><th>누가</th><th>DSL 로 표현</th><th>손으로 쓴 것 / DSL 밖</th></tr></thead>
        <tbody>
          <tr><td><a href="#browse">1. 상품 둘러보기</a></td><td>누구나</td><td>''' + cov('all') + '''</td><td>-</td></tr>
          <tr><td><a href="#catalog-admin">2. 상품 관리</a></td><td>관리자</td><td>''' + cov('most') + '''</td><td>"품목이 있는 상품은 못 지움" 은 DB 외래 키로만. 관리 화면 없음</td></tr>
          <tr><td><a href="#signup">3. 회원 가입</a></td><td>방문자</td><td>''' + cov('most') + '''</td><td>가입 후 바로 로그인(세션) · 비밀번호 최소 길이는 넣지 않음 (데모 계정)</td></tr>
          <tr><td><a href="#signin">4. 로그인·로그아웃</a></td><td>누구나</td><td>''' + cov('hand') + '''</td><td>로그인 API 3개(세션)는 손으로. 비밀번호 확인은 생성</td></tr>
          <tr><td><a href="#profile">5. 내 정보</a></td><td>고객</td><td>''' + cov('all') + '''</td><td>(현재 비밀번호 확인 없이 비밀번호 변경)</td></tr>
          <tr><td><a href="#cart">6. 장바구니</a></td><td>누구나</td><td>''' + cov('none') + '''</td><td>브라우저에만 있음 (의도)</td></tr>
          <tr><td><a href="#checkout">7. 주문(결제)</a></td><td>고객</td><td>''' + cov('most') + '''</td><td>가격 정책(VAT·할인)·기본 배송지·주문일 · 결제 승인·재고는 모델에 없음</td></tr>
          <tr><td><a href="#orders">8. 주문 조회</a></td><td>고객·관리자</td><td>''' + cov('all') + '''</td><td>-</td></tr>
          <tr><td><a href="#cancel">9. 주문 취소</a></td><td>고객</td><td>''' + cov('all') + '''</td><td>환불은 모델에 없음</td></tr>
          <tr><td><a href="#ship">10. 배송</a></td><td>관리자</td><td>''' + cov('all') + '''</td><td>-</td></tr>
        </tbody>
      </table>
''')

# ---- 1 browse --------------------------------------------------------------------------
page.append('''
      <h2 id="browse">1. 상품 둘러보기 ''' + cov('all') + '''</h2>
      <p class="biz">손님은 로그인 없이 분류 → 상품 → 품목 순으로 둘러보고, 이름으로 품목을 찾습니다. 품목 카드에는 상품 이름과 가격이 함께 나옵니다.</p>
''' + rules([
    ('누구나 본다', '<code>@access(read=public)</code>', '방문자 검색·조회 200'),
    ('분류 → 상품 → 품목', '<code>category: Category @ManyToOne</code><br><code>product: Product @ManyToOne</code>', '<code>{"categoryId":1001}</code> 로 개(Dogs) 상품만'),
    ('이름 일부로 찾기', '<code>name: string(30)! @search(like)</code>', '<code>{"name":"%Puppy%"}</code> → 2건 (화면이 <code>%</code> 를 붙여 보냄)'),
    ('품목에 상품 이름도', 'dto.yml <code>productName: string @ref(product.name)</code>', '응답에 <code>productName</code>'),
    ('가격은 금액', '<code>unitCost: money(10.2)!</code>', 'JSON 숫자 10.00, Java <code>Money</code>'),
    ('배송 국가 목록', '<code>Country … @access(read=public) @expose(300)</code>', '결제 화면의 국가 선택'),
    ('잘못된 검색은 요청 잘못', '(생성)', '없는 정렬 컬럼 → 400 "No property \'nope\' found for type \'Item\'"'),
]) + '''
      ''' + code('catalog') + '''
      <p>다른 애그리거트는 객체가 아니라 <b>식별자</b>로 참조합니다(<code>productId</code>). <code>env.yml</code> 의
        <code>referenceColumn: short</code> 로 컬럼 이름이 <code>product_id</code> 가 됩니다.</p>
''')
SECTIONS.append(src('catalog', '\n\n'.join([block(ENTITY, n) for n in ('Category', 'Product', 'Item', 'Country')]) + '\n\n' + block(DTO, 'ItemDTO')))

# ---- 2 catalog admin -------------------------------------------------------------------------
page.append('''
      <h2 id="catalog-admin">2. 상품 관리 ''' + cov('most') + '''</h2>
      <p class="biz">관리자가 분류·상품·품목·국가를 등록·수정·삭제하고 가격을 바꿉니다.</p>
''' + rules([
    ('관리자만 바꾼다', '<code>@access(read=public)</code> 에 create·update·delete 를 <b>적지 않음</b>', '방문자 401, 고객 403, 관리자 200'),
    ('가격은 0 보다 크다', '<code>@min(0.01, \'price must be positive\')</code>', '0 → 400 "unitCost: price must be positive" (화면 검증 함수에도 같은 문구)'),
    ('값을 바꿔도 지난 주문은 그대로', '주문 줄 <code>unitCost … @snapshot(item.unitCost)</code>', '10.00 → 11.50 으로 바꾼 뒤에도 기존 주문 줄은 10.00'),
    ('품목이 있는 상품은 못 지운다', '<b>DSL 에 규칙 없음</b> - DB 외래 키', '409 "The row is still referenced by another…"'),
]) + '''
      <div class="gap"><b>DSL 밖:</b> "품목이 남아 있으면 삭제할 수 없다" 는 업무 규칙이지만 DSL 에 쓸 곳이 없어서 DB 외래 키 오류(409)로만 지켜집니다.
        관리 화면은 만들지 않았습니다 - <a href="/swagger-ui.html">Swagger</a> 에서 관리자로 로그인한 세션으로 API 를 부릅니다.</div>
''')

# ---- 3 sign up -------------------------------------------------------------------------------
page.append('''
      <h2 id="signup">3. 회원 가입 ''' + cov('most') + '''</h2>
      <p class="biz">방문자가 로그인 ID·비밀번호·이름·집 주소(필수)·연락처로 가입하면 일반 고객이 되고 바로 로그인됩니다.</p>
''' + rules([
    ('누구나 가입', '<code>@access(create=public)</code>', '201'),
    ('로그인 ID 는 하나뿐', '<code>@Index(name="uk_ps_customer_login", columnList="login", unique="true")</code>', '같은 ID → 409 "A unique value is already taken."'),
    ('ID 10자 이하, 이름 필수', '<code>login: string(10)!</code>, <code>firstName: string(50)!</code>', '11자 이상 → 400'),
    ('이메일 형식, 생일은 과거', '<code>@email</code>, <code>@past</code>', '400 "Email format invalid", "Date must be past"'),
    ('집 주소 필수', '<code>homeAddress: Address! @Embedded</code> + Address 의 <code>street1: string(50)!</code> …', '주소 없음 → 400 "homeAddress: …"'),
    ('비밀번호는 해시로만, 돌려주지 않음', '<code>password: string(255)! @password</code>', '응답에 password 없음, DB 에는 PBKDF2 해시'),
    ('스스로 관리자가 될 수 없다', '<code>role: UserRole @default(UserRole.USER) @access(admin)</code>', '<code>"role":"A"</code> 로 보내도 <code>U</code>'),
    ('가입하면 바로 로그인', '<b>손으로</b> - <code>AccountController.signUp</code>', '가입 응답의 세션으로 <code>/customer/me</code> 200'),
]) + '''
      ''' + code('customer') + '''
      <div class="two">
        <div><p class="label">생성: 가입·수정 때 역할 보호와 해시</p>''' + code('gen-customer', 'java') + '''</div>
        <div><p class="label">손으로: 가입하고 바로 로그인 (세션 시작)</p>''' + code('gen-account', 'java') + '''</div>
      </div>
      <div class="gap"><b>이 샘플에 없는 것:</b> 비밀번호 최소 길이. <code>password: string(255)! @password @length(min=8)</code> 이면
        보낸 비밀번호를 해시하기 전에 검사해 7자는 400 입니다(DTO 의 <code>@Size(min=8, max=255)</code>). 이 샘플은 원본의 데모 계정
        <code>user/user</code> 를 쓰므로 넣지 않았습니다. 복잡도(문자 종류)는 <code>@pattern</code> 으로 씁니다.</div>
''')
SECTIONS.append(src('customer', block(ENTITY, 'Address') + '\n\n' + block(ENTITY, 'Customer')))
SECTIONS.append(src('gen-customer', '// CustomerCoreService (생성)\n' + G_CUSTOMER))
SECTIONS.append(src('gen-account', '// adapter/in/web/shop/AccountController (손으로)\n' + G_ACCOUNT))

# ---- 4 sign in ---------------------------------------------------------------------------------
page.append('''
      <h2 id="signin">4. 로그인·로그아웃 ''' + cov('all') + '''</h2>
      <p class="biz">고객은 로그인 ID 와 비밀번호로 로그인하고, 로그아웃합니다. 화면은 접속할 때 "지금 누구인지" 를 묻습니다.</p>
''' + rules([
    ('ID 와 비밀번호가 맞아야', '<code>login … @login</code> + <code>password … @password</code> → 생성 <code>authenticate(login, password)</code>', '틀린 비밀번호·없는 ID 모두 401 (같은 시간이 걸리게 - 어느 쪽인지 알리지 않음)'),
    ('로그인 유지', '엔티티 줄의 <code>@signin</code> → 생성 <code>POST /customer/signin</code>: 세션에 고객 ID 와 역할(<code>role ... @access(admin)</code> 필드)을 넣음', '200, 이후 요청은 그 고객으로'),
    ('로그아웃', '생성 <code>POST /customer/signout</code>', '204, 이후 <code>/customer/me</code> 401'),
    ('지금 누구인가', '생성 <code>GET /customer/me</code>', '방문자 401, 고객 200'),
    ('로그인 안 했으면 방문자', '런타임 <code>SessionCurrentUser</code> (요청 밖에서는 아무도 아님)', '-'),
]) + '''
      <div class="two">
        <div><p class="label">생성: 비밀번호 확인</p>''' + code('gen-auth', 'java') + '''</div>
        <div><p class="label">생성: 로그인 API (CustomerController)</p>''' + code('gen-signin', 'java') + '''</div>
      </div>
      <div class="gap"><b>로그인 API 는 생성됩니다:</b> <code>@login</code>·<code>@password</code> 가 있는 <code>Customer</code> 의 줄에 <code>@signin</code> 을 쓰면
        그 컨트롤러에 <code>signin</code>·<code>signout</code>·<code>me</code> 가 붙습니다. 화면은 생성된 TS 클라이언트의 <code>signIn</code>·<code>signOut</code>·<code>me</code> 를 씁니다.
        역할은 관리자만 쓰는 <code>role</code> 필드라서 세션에 들어갑니다 - 그렇지 않은 필드면 가입자가 스스로 관리자가 될 수 있어 빌드가 경고하고 넣지 않습니다.</div>
''')
SECTIONS.append(src('gen-auth', '// CustomerCoreService (생성) - @login + @password\n' + G_AUTH))
SECTIONS.append(src('gen-signin', '// CustomerController (생성) - @login + @password + @expose\n' + G_SIGNIN))

# ---- 5 profile ---------------------------------------------------------------------------------
page.append('''
      <h2 id="profile">5. 내 정보 ''' + cov('all') + '''</h2>
      <p class="biz">고객은 자기 이름·연락처·집 주소·비밀번호를 고칩니다. 관리자는 모든 고객을 보고 역할을 바꿉니다.</p>
''' + rules([
    ('자기 것만 보고 고친다', '<code>@owner(customerId) @access(read=owner, update=owner)</code>', '고객 검색 결과는 본인 1건, 남의 정보 GET·PUT → 404'),
    ('역할은 관리자만', '<code>role … @access(admin)</code>', '본인이 <code>"role":"A"</code> → 무시(U 유지), 관리자가 바꾸면 A'),
    ('비밀번호를 바꾸면 다시 해시', '<code>@password</code>', '새 비밀번호로 로그인 200, 옛 비밀번호 401'),
    ('탈퇴는 관리자만', 'delete 를 적지 않음', '본인 DELETE → 403'),
]) + '''
      <div class="gap"><b>업무에 따라 부족한 것:</b> 비밀번호 변경이 일반 수정(PUT)이라 <b>현재 비밀번호를 확인하지 않습니다</b>.
        필요하면 <code>commands: changePassword: current, next</code> 로 명령을 선언하고 확인 로직은 행위 클래스에 씁니다.
        고객이 스스로 탈퇴하게 하려면 <code>@access(…, delete=owner)</code> 한 단어면 됩니다.</div>
''')

# ---- 6 cart ------------------------------------------------------------------------------------
page.append('''
      <h2 id="cart">6. 장바구니 ''' + cov('none') + '''</h2>
      <p class="biz">손님은 품목을 담고, 수량을 바꾸고, 비웁니다. 로그인하지 않아도 담을 수 있고, 결제할 때 로그인합니다.</p>
      <p>장바구니는 <b>브라우저(localStorage)</b> 에만 있습니다 (<code>web/src/cart.ts</code>, 손으로). 서버에는 결제 순간에 <b>품목 ID 와 수량만</b> 갑니다.
        화면에 보이는 가격은 안내용이고, 실제 가격은 서버가 결제 때 다시 매깁니다(7번 <code>@snapshot</code>).</p>
      <div class="gap"><b>DSL 밖 (의도):</b> 결제 전의 장바구니는 업무 기록이 아니라고 보고 서버 모델에 넣지 않았습니다.
        "기기를 바꿔도 장바구니 유지" 가 업무라면 <code>Cart</code>·<code>CartLine</code> 엔티티와 <code>@owner</code> 로 DSL 에 쓰면 됩니다.</div>
''')

# ---- 7 checkout ----------------------------------------------------------------------------------
page.append('''
      <h2 id="checkout">7. 주문 (결제) ''' + cov('most') + '''</h2>
      <p class="biz">로그인한 고객이 장바구니를 결제합니다. 배송지(비우면 집 주소)와 카드를 넣으면 주문이 <b>접수(PLACED)</b> 됩니다.
        금액 = 줄 합계(단가 × 수량의 합) + VAT 5.5% − 할인 2.25%(VAT 포함 금액에서), 센트 단위 반올림.</p>
''' + rules([
    ('로그인한 고객만, 자기 이름으로', '<code>@owner(customerId) @access(create=user)</code>', '방문자 401. <code>"customerId":1</code> 을 보내도 주인은 로그인한 고객(2)'),
    ('한 줄 이상', '<code>lines … @minsize(1, \'the cart is empty\')</code>', '빈 장바구니 → 400 "the cart is empty"'),
    ('수량은 1 이상', '<code>quantity: quantity! @min(1)</code>', '0 → 400'),
    ('카드 필수, 카드 번호 필수', '<code>creditCard: CreditCard! @Embedded</code>, <code>creditCardNumber: string(30)!</code>', '카드 없음·번호 없음 → 400'),
    ('카드 번호는 돌려주지 않는다', '<code>creditCardNumber … @writeonly</code>', '주문 응답·조회에 번호 없음 (종류·만료일만)'),
    ('단가 = 주문 당시 가격', '<code>unitCost: money(10.2) @snapshot(item.unitCost)</code>', '<code>"unitCost":0.01</code> 을 보내도 10.00. 나중에 품목 가격이 바뀌어도 그대로'),
    ('없는 품목은 주문 불가', '(생성된 참조 확인 <code>requireReferences</code>)', '404 "Item 99999 does not exist"'),
    ('소계·합계', '<code>subTotal … @compute(unitCost * quantity)</code><br><code>totalWithoutVat … @compute(sum(lines.subTotal))</code>', '2 × 10.00 + 10.00 = 30.00'),
    ('상태·금액은 요청으로 못 정함', '<code>states:</code>, <code>@behavior</code>, <code>@compute</code> → 읽기 전용', '<code>"status":"SHP","total":1</code> 을 보내도 PLC · 30.94'),
    ('새 주문은 접수 상태', '<code>states: status: initial: PLACED</code>', 'PLC'),
    ('VAT 5.5% 더하고 할인 2.25%', '<b>손으로</b> - <code>PurchaseOrder.place()</code>, 요율은 <code>application.yml</code>', 'VAT 1.65, 할인 0.71, 합계 30.94'),
    ('배송지 없으면 집 주소', '<b>손으로</b> - <code>place()</code>', '배송지 비우면 집 주소, 넣으면 그 주소'),
    ('주문일은 오늘', '<b>손으로</b> - <code>place()</code> (테스트용 <code>Clock</code>)', '2026-10-03'),
    ('접수되면 알린다', '<code>@event(Placed(total))</code> + 손으로 <code>raisePlaced()</code>', '커밋 때 <code>PurchaseOrderPlacedEvent</code>'),
]) + '''
      ''' + code('order') + '''
      ''' + code('line') + '''
      <div class="two">
        <div><p class="label">생성: 저장 직전에 채워지는 값</p>''' + code('gen-derive', 'java') + '''</div>
        <div><p class="label">손으로: 가격 정책</p>''' + code('gen-place', 'java') + '''</div>
      </div>
      <h3>결제 한 건이 처리되는 순서</h3>
      <ol class="flow">
        <li>요청 형식 <span class="tag gen">생성</span><em>DTO 검증: 줄 1개 이상, 수량 ≥ 1, 카드 필수 → 실패면 400</em></li>
        <li>누가 <span class="tag gen">생성</span><em><code>create=user</code>: 로그인 안 했으면 401, 주인 = 로그인한 고객</em></li>
        <li>DTO → 엔티티 <span class="tag gen">생성</span><em>품목·수량·주소·카드만 옮김 (가격·상태·합계는 읽기 전용)</em></li>
        <li>파생 값 <span class="tag gen">생성</span><em><code>requireReferences()</code>: 없는 품목 404, <code>derive()</code>: 줄 단가 스냅샷 → 소계 → 합계</em></li>
        <li>가격 정책 <span class="tag hand">손으로</span><em><code>place()</code>: 주문일, 기본 배송지, VAT, 할인, 반올림, <code>raisePlaced()</code></em></li>
        <li>불변식 <span class="tag gen">생성</span><em>저장 직전 다시 확인: 줄이 없으면 "the cart is empty"</em></li>
        <li>저장·이벤트 <span class="tag gen">생성</span><em>상태 PLACED 로 저장, 커밋 때 접수 이벤트</em></li>
      </ol>
      <div class="gap"><b>DSL 로 못 하는 것:</b>
        <br>• <b>가격 정책</b> - 설정 값(VAT 5.5%, 할인 2.25%)을 쓰는 계산, 계산 순서, 반올림. <code>@compute</code> 는 필드끼리의 곱·합만 됩니다.
        이 샘플에 남은 유일한 도메인 코드입니다. 개선 후보: <code>policy:</code> (요율을 설정에서 읽는 계산).
        <br>• <b>기본값 규칙</b> - "배송지가 없으면 집 주소", "주문일은 오늘".
        <br>• <b>결제 승인·재고</b> - 모델에 없습니다 (원본 Java EE pet store 에도 없음). 카드사 연동은 DSL 의 <code>port.yml</code>(HTTP 포트)로 선언할 수 있지만 이 샘플은 쓰지 않습니다.</div>
''')
SECTIONS.append(src('order', ORDER_BLOCK))
SECTIONS.append(src('line', block(ENTITY, 'OrderLine') + '\n\n' + block(DTO, 'OrderLineDTO')))
SECTIONS.append(src('gen-derive', '// OrderLineBase (생성) - @snapshot, @compute\n' + G_LINE + '\n\n// PurchaseOrderBase (생성) - @compute(sum(lines.subTotal))\n' + G_ORDER_DERIVE))
SECTIONS.append(src('gen-place', '// domain/model/PurchaseOrder (손으로)\n' + G_PLACE))

# ---- 8 orders ------------------------------------------------------------------------------------------
page.append('''
      <h2 id="orders">8. 주문 조회 ''' + cov('all') + '''</h2>
      <p class="biz">고객은 자기 주문 목록과 상세(줄·금액·배송지·카드 종류)를 봅니다. 관리자는 모든 주문을 봅니다.</p>
''' + rules([
    ('자기 주문만', '<code>@owner(customerId) @access(read=owner)</code>', '검색은 본인 것만. <code>{"customerId":1}</code> 로 찾아도 0건. 남의 주문 404, 방문자 401'),
    ('관리자는 전부', '(관리자는 모든 수준을 가짐)', '관리자 검색은 모든 고객의 주문'),
    ('줄에 품목 이름', 'dto.yml <code>itemName: string @ref(item.name)</code>', '줄마다 <code>itemName</code> ("Large")'),
    ('최신순', '<b>화면이</b> <code>sortColumn: orderDate</code> 를 보냄', 'DSL 에 기본 정렬 없음'),
]) + '''
      ''' + code('gen-owned', 'java') + '''
''')
SECTIONS.append(src('gen-owned', '// PurchaseOrderCoreService (생성) - read=owner, create=user\n' + G_OWNED))

# ---- 9 cancel ------------------------------------------------------------------------------------------
page.append('''
      <h2 id="cancel">9. 주문 취소 ''' + cov('all') + '''</h2>
      <p class="biz">고객은 자기 주문을 <b>배송 전(접수 상태)</b> 에만 취소할 수 있습니다. 화면은 취소할 수 있을 때만 버튼을 보입니다.</p>
''' + rules([
    ('자기 주문만', '<code>cancel=owner</code>', '남의 주문 → 404'),
    ('접수 상태에서만', '<code>states: status: cancel: PLACED -> CANCELLED</code>', '두 번째 취소·배송 후 취소 → 409 "cannot cancel when status is SHIPPED; it must be [PLACED]"'),
    ('취소되면 알린다', '<code>@event(… Cancelled)</code>', '<code>PurchaseOrderCancelledEvent</code>'),
    ('버튼은 할 수 있을 때만', '(생성) TypeScript <code>Orders.canCancel(order)</code>', '서버의 <code>canCancel()</code> 과 같은 판단'),
]) + '''
      <div class="gap"><b>모델에 없는 것:</b> 환불. 결제 모델이 없어서 취소는 상태만 바꿉니다. 결제 상태가 생기면
        같은 <code>cancel</code> 명령이 두 생명주기(주문·결제)를 함께 바꾸게 쓸 수 있습니다 - <a href="#lifecycle">주문 생명주기</a> 참고.</div>
''')

# ---- 10 ship --------------------------------------------------------------------------------------------
page.append('''
      <h2 id="ship">10. 배송 (백오피스) ''' + cov('all') + '''</h2>
      <p class="biz">관리자는 백오피스에서 모든 주문을 보고, 접수된 주문을 배송 처리합니다. 주문은 고칠 수 없고, 지우는 것은 관리자뿐입니다.</p>
''' + rules([
    ('관리자만', '<code>@access</code> 에 ship 을 적지 않음', '고객 → 403 "ship PurchaseOrder is for the administrator"'),
    ('접수 상태에서만', '<code>states: status: ship: PLACED -> SHIPPED</code>', '취소된 주문 배송 → 409'),
    ('배송되면 알린다', '<code>@event(… Shipped)</code>', '<code>PurchaseOrderShippedEvent</code>'),
    ('버튼은 할 수 있을 때만', '(생성) TypeScript <code>Orders.canShip(order)</code>', '-'),
    ('주문은 고칠 수 없다', '<code>@editable</code> 이 아닌 루트 → 수정 API 없음', 'PUT → 405'),
    ('삭제는 관리자만', 'delete 를 적지 않음', '고객 403, 관리자 200'),
]) + '''
''')

# ---- lifecycle ------------------------------------------------------------------------------------------
page.append('''
      <h2 id="lifecycle">주문 생명주기 (<code>states:</code>)</h2>
      <div class="states">
        <span class="st">PLACED</span>
        <span class="arrow"><b>→</b>ship <small>(관리자)</small></span>
        <span class="st end">SHIPPED</span>
        <span style="width:24px"></span>
        <span class="st">PLACED</span>
        <span class="arrow"><b>→</b>cancel <small>(본인)</small></span>
        <span class="st end">CANCELLED</span>
      </div>
      <ul>
        <li><code>states:</code> 바로 아래 키 <code>status</code> 는 <b>필드 이름</b>입니다 - <code>fields:</code> 의 <code>status: OrderStatus</code>.</li>
        <li>상태 이름(<code>PLACED</code>…)은 그 필드 타입인 <code>OrderStatus</code> enum 의 <b>상수 이름</b>입니다 (DB·JSON 코드 <code>PLC</code> 가 아님).
          enum 에 없는 이름이면 빌드가 경고하고 그 생명주기를 만들지 않습니다.</li>
        <li><code>initial</code> 은 시작 상태, 나머지 키는 <b>명령</b>이고 값은 <code>시작 상태 -&gt; 도착 상태</code> 입니다. 명령마다 API(<code>POST …/{id}/ship</code>)가 생깁니다.</li>
        <li><b>SHIPPED 다음은?</b> 나가는 화살표가 없는 상태는 <b>끝</b>입니다. 다음 단계가 필요하면 enum 에 <code>DELIVERED</code> 를 더하고 <code>deliver: SHIPPED -&gt; DELIVERED</code> 한 줄.</li>
        <li>상태 필드가 둘 이상(주문 상태·결제 상태)이면 필드마다 생명주기를 쓰고, <b>같은 명령 이름</b>을 두 곳에 두면 그 명령은 둘을 함께 바꿉니다 -
          모든 생명주기를 먼저 확인하고, 하나라도 아니면 아무것도 바꾸지 않습니다.</li>
      </ul>
      ''' + code('gen-ship', 'java') + '''

      <h2 id="metrics">업무 지표와 업무 용어 (<code>metrics:</code>, <code>@terms</code>)</h2>
      <p class="biz">가게가 보는 숫자 - <b>매출</b>(취소 제외, VAT 포함·할인 후), <b>주문 수</b>, <b>객단가</b>(주문 1건 평균), 배송·취소 건수, 고객 수 -
        도 업무 정보입니다. 화면·SQL·BI 도구마다 "매출" 을 따로 정의하면 숫자가 달라지므로, DSL 에 <b>한 번</b> 적습니다.</p>
      ''' + code('metrics') + '''
      <ul>
        <li>한 줄이 지표 하나: <code>sum|avg|min|max|count(엔티티.필드)</code>, 조건은 <code>where 필드 != 값 and …</code>, 지표끼리는 <code>revenue / orderCount</code>.</li>
        <li>enum 은 상수 이름으로 씁니다(<code>CANCELLED</code>) - 내보낼 때 저장 코드(<code>'CAN'</code>)로 바뀝니다.</li>
        <li><code>@terms(…)</code> 는 업무에서 부르는 이름·동의어입니다. 엔티티(<code>주문 = order, purchase, 구매</code>)·필드(<code>가격</code>)·지표에 붙입니다.</li>
        <li>애플리케이션 코드는 만들지 않습니다. Apache Ossie 시맨틱 모델의 <code>metrics</code> 와 <code>ai_context.synonyms</code> 로 나가서,
          BI·AI 도구가 "매출" 을 물으면 이 정의를 씁니다.</li>
      </ul>
      ''' + code('ossie-metric') + '''
      <p><b>확인:</b> <code>MetricsTest</code> 가 주문을 접수·배송·취소한 뒤, 내보낸 지표 SQL 을 실제 DB 에서 실행한 값과 엔티티로 직접 계산한 값이
        같은지 봅니다 (매출, 주문 수, 객단가, 배송·취소 건수, 고객 수).</p>
      <div class="gap"><b>아직 못 하는 것:</b> 지표는 <b>한 엔티티 안에서</b> 셉니다. "취소되지 않은 주문의 팔린 수량" 처럼 주문 줄을 주문 상태로 거르는 지표는
        쓸 수 없어서 넣지 않았습니다 - 억지로 쓰면 취소분까지 셉니다. 기간(월별)은 BI 도구가 <code>orderDate</code> 로 나눕니다.</div>

      <h2 id="errors">오류 응답</h2>
      <p>모든 오류는 같은 모양(<code>ProblemDetail</code>: <code>status</code>, <code>title</code>, <code>detail</code>, 프로그램이 분기하는 <code>code</code>,
        필드 오류는 <code>errors</code> 와 <code>fieldErrors</code>)입니다. 서비스가 거절한 것은 생성된 <code>PetstoreApiErrors</code> 가,
        컨트롤러에 닿기 전에 Spring MVC 가 거절한 것(JSON 이 아닌 본문, 타입이 틀린 값, 없는 경로·메서드)은 그 파일이 가져오는
        런타임의 <code>RequestShapeErrors</code> 가 만듭니다. 문구는 요청의 언어(<code>Accept-Language</code>, <code>petstore-messages_ko.properties</code>)로,
        헤더가 없거나 번역이 없는 언어면 영어로 나갑니다 - 서버 머신의 언어로 나가지 않습니다(<code>ShopFlowTest</code> 가 한국어 JVM 에서 확인).</p>
      <table class="map">
        <thead><tr><th>상태</th><th>뜻</th><th>예 (확인함)</th></tr></thead>
        <tbody>
          <tr><td>400</td><td>요청이 틀림 - DSL 의 검증, 잘못된 검색, 필수 값 누락, 타입이 받지 않는 값, JSON 이 아닌 본문</td><td>"lines: the cart is empty", "No property 'nope'…", "A required value is missing.", 수량 -1 (<code>lines[0].quantity</code>, <code>typeMismatch</code>), 본문 <code>{"lines":</code> (<code>unreadable</code>)</td></tr>
          <tr><td>401</td><td>로그인 필요</td><td>"Sign in to create PurchaseOrder"</td></tr>
          <tr><td>403</td><td>이 사람에게 허락되지 않음</td><td>"ship PurchaseOrder is for the administrator"</td></tr>
          <tr><td>404</td><td>없음 - 또는 남의 것, 또는 요청이 가리킨 행이 없음, 또는 없는 경로</td><td>남의 주문·고객 정보, "Item 99999 does not exist", 없는 상품 GET ("Item not found")</td></tr>
          <tr><td>405</td><td>그런 연산이 없음</td><td>주문 PUT (<code>method-not-allowed</code>, <code>Allow: POST</code>)</td></tr>
          <tr><td>409</td><td>저장된 데이터나 지금 상태와 충돌</td><td>"A unique value is already taken." (같은 로그인 ID), 품목이 남은 상품 삭제, "cannot cancel when status is SHIPPED…"</td></tr>
          <tr><td>422</td><td>도메인의 다른 규칙이 거절 (불변식)</td><td>-</td></tr>
        </tbody>
      </table>

      <h2 id="hand">손으로 쓴 것</h2>
      <table class="map">
        <thead><tr><th>파일</th><th>내용</th><th>왜 DSL 이 아닌가</th></tr></thead>
        <tbody>
          <tr><td><code>domain/model/PurchaseOrder.java</code></td><td>가격 정책 <code>place()</code>: VAT·할인·반올림, 기본 배송지, 주문일</td><td>설정 값을 쓰는 계산·기본값 규칙 문법 없음</td></tr>
          <tr><td><code>application/PurchaseOrderService.java</code></td><td>요율을 설정에서 읽고, 처음 저장될 때 <code>place()</code></td><td>위와 같음</td></tr>
          <tr><td><code>adapter/in/web/shop/AccountController.java</code></td><td>가입하고 바로 로그인</td><td>가입과 로그인을 한 요청으로 묶는 것은 화면의 선택 (로그인·로그아웃·me 는 생성)</td></tr>
          <tr><td><code>DemoAccounts.java</code></td><td>데모 계정 admin/admin, user/user</td><td>기준·데모 데이터 문법 없음 (<code>data.sql</code> 도 손으로)</td></tr>
          <tr><td><code>OrderLine.java</code>, <code>CustomerService.java</code>, <code>PetstoreApplication.java</code></td><td>빈 클래스, <code>Clock</code> 빈</td><td>-</td></tr>
          <tr><td><code>web/src/*.ts</code> (540줄)</td><td>화면</td><td>화면은 DSL 의 대상이 아님. 생성된 TS 클라이언트(타입·API·검증·라벨·<code>canX</code>)를 씀</td></tr>
        </tbody>
      </table>
''')
SECTIONS.append(src('gen-ship', '// PurchaseOrderBase (생성) - states: ship: PLACED -> SHIPPED\n' + G_SHIP))

# ---- review ---------------------------------------------------------------------------------------------
page.append('''
      <h2 id="review">검토: 이 가게의 업무를 DSL 로 다 표현할 수 있나</h2>
      <p>위 업무별 표의 규칙 <b>@@TOTAL@@개</b>(표의 행) 중 DSL 로 쓴 것 <b>@@DSL@@개</b>, 손으로 쓴 것 <b>@@HAND@@개</b>,
        DSL 에 쓸 곳이 없어 DB 제약으로만 지켜지는 것 <b>@@NONE@@개</b>, 화면이 정하는 것 <b>@@SCREEN@@개</b>입니다.
        표에 행으로 넣지 않은 빈 곳 - 비밀번호 최소 길이, 비밀번호 변경 때 현재 비밀번호 확인, 결제 승인·환불·재고(모델에 없음),
        장바구니(브라우저에만, 의도) - 은 각 업무의 상자에 적었습니다. DSL 에 넣으려면 필요한 것:</p>
      <table class="map">
        <thead><tr><th>#</th><th>DSL 에 없는 것</th><th>지금은</th><th>제안</th></tr></thead>
        <tbody>
          <tr><td>1</td><td>업무 지표 - 매출, 주문 수, 객단가</td><td><b>완료</b> - <a href="#metrics"><code>metrics:</code></a></td><td>Ossie <code>metrics</code> 로 내보냄. 다른 엔티티 조건이 필요한 지표는 아직 안 됨</td></tr>
          <tr><td>2</td><td>업무 용어 - 주문 = 구매·order</td><td><b>완료</b> - <a href="#metrics"><code>@terms</code></a></td><td>Ossie <code>ai_context.synonyms</code></td></tr>
          <tr><td>3</td><td>가격 정책 - 설정 값으로 계산</td><td>손으로 <code>place()</code></td><td><code>policy:</code> - <code>vat: total * config(vat-rate) / 100</code> 같은 계산</td></tr>
          <tr><td>4</td><td>기본값 규칙 - 배송지 = 집 주소, 주문일 = 오늘</td><td>손으로</td><td><code>@default(customer.homeAddress)</code>, <code>@default(today)</code></td></tr>
          <tr><td>5</td><td>로그인 API</td><td><b>완료</b> - <a href="#signin"><code>@login</code> + <code>@password</code></a></td><td>signin·signout·me 와 TS 클라이언트 생성. 가입 후 자동 로그인은 손으로</td></tr>
          <tr><td>6</td><td>비밀번호 최소 길이</td><td><b>된다</b> - <code>@length(min=8)</code> 이 해시 전에 검사됨 (이 샘플은 데모 계정 때문에 넣지 않음)</td><td>-</td></tr>
          <tr><td>7</td><td>삭제 조건 - 품목이 있는 상품은 못 지움</td><td>DB 외래 키 409</td><td><code>@deleteWhen(items empty)</code> 같은 규칙 → 409 와 업무 문구</td></tr>
          <tr><td>8</td><td>기준·데모 데이터</td><td><code>data.sql</code>, <code>DemoAccounts</code></td><td><code>data:</code> 블록 (가입과 같은 규칙 - 해시 등 - 을 거쳐 넣음)</td></tr>
          <tr><td>9</td><td>기본 정렬</td><td>화면이 지정</td><td><code>@expose(20, sort=orderDate desc)</code></td></tr>
        </tbody>
      </table>
      <p>나머지 - 누가 무엇을 하는지, 주문의 생명주기, 가격 스냅샷, 합계, 필수·형식 검증, 비밀번호 저장, 민감 정보 숨김, 참조, 이벤트 - 는 DSL 에 있고,
        서버·화면 코드와 Ossie 모델이 모두 거기서 생성됩니다.</p>

      <h2 id="ossie">Apache Ossie 시맨틱 모델</h2>
      <p>빌드할 때마다 같은 DSL 에서 <code>blueprint/semantic_model.yml</code> 이 만들어집니다 (<a href="https://github.com/apache/ossie">Apache Ossie</a> core spec 0.2.0.dev0).
        분석·BI·AI 도구가 이 파일로 테이블의 뜻을 압니다: 컬럼의 타입, 조인, 상태 코드의 뜻(<code>PLC = Placed</code>), 그리고 비밀번호·카드 번호 컬럼은 <b>내보내지 않습니다</b>.
        내보낸 모든 테이블·컬럼·키·조인이 실제 DB 에 있는지 빌드할 때마다 테스트(<code>SemanticModelTest</code>)가 확인합니다.
        Ossie 공식 스키마(<code>ossie-schema.json</code>)로 직접 검증한 결과 오류는 0 이었습니다(2026-10-03).</p>
      ''' + code('ossie') + '''

      <h2 id="all-dsl">전체 DSL</h2>
      <details><summary>env.yml</summary>''' + code('env') + '''</details>
      <details><summary>model/enum.yml</summary>''' + code('enum') + '''</details>
      <details><summary>model/entity.yml</summary>''' + code('entity') + '''</details>
      <details><summary>model/dto.yml</summary>''' + code('dto') + '''</details>
      <details><summary>model/metrics.yml</summary>''' + code('metrics') + '''</details>
      <p class="back"><a href="/">← 쇼핑몰로 돌아가기</a></p>
    </article>
  </div>

  <footer>ElastiCORE 3.0 sample · <a href="/">YAPS Pet Store</a> · <a href="/swagger-ui.html">API</a></footer>

  <!-- Built by guide/build_guide.py from blueprint/petstore and the generated sources; rebuild rather than edit. -->
''')
SECTIONS.append(src('ossie', semantic_dataset('PurchaseOrder').rstrip()))
SECTIONS.append(src('metrics', METRICS))
SECTIONS.append(src('ossie-metric', '# blueprint/semantic_model.yml (생성)\n' + semantic_metric('revenue').rstrip()))
SECTIONS.append(src('env', ENV))
SECTIONS.append(src('enum', ENUM))
SECTIONS.append(src('entity', ENTITY.rstrip()))
SECTIONS.append(src('dto', DTO))

body = ''.join(page)
total = sum(COUNTS.values())
for k, v in (('@@TOTAL@@', total), ('@@DSL@@', COUNTS['dsl']), ('@@HAND@@', COUNTS['hand']),
             ('@@NONE@@', COUNTS['none']), ('@@SCREEN@@', COUNTS['screen'])):
    body = body.replace(k, str(v))
print('rule counts:', COUNTS, 'total', total)
text = body + ''.join('  ' + s for s in SECTIONS) + '\n  ' + HIGHLIGHT + '\n</body>\n</html>\n'
io.open(OUT, 'w', encoding='utf-8', newline='\n').write(text)
print('written', len(text), 'chars')
