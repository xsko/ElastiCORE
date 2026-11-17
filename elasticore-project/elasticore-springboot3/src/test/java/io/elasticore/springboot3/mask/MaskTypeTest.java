package io.elasticore.springboot3.mask;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MaskTypeTest {

    @Test
    void aPhoneNumberKeepsItsFirstThreeAndLastFourDigitsAndItsDashes() {
        assertEquals("010-****-5678", MaskType.PHONE.apply("010-1234-5678"));
        assertEquals("010****5678", MaskType.PHONE.apply("01012345678"));
        assertEquals("02-***-4567", MaskType.PHONE.apply("02-123-4567"));
        assertEquals("****", MaskType.PHONE.apply("1234"));
    }

    @Test
    void anEmailKeepsTwoLettersOfItsLocalPartAndItsDomain() {
        assertEquals("ho**********@example.com", MaskType.EMAIL.apply("hong.gildong@example.com"));
        assertEquals("a*@example.com", MaskType.EMAIL.apply("ab@example.com"));
        assertEquals("no******", MaskType.EMAIL.apply("not-mail"));
    }

    @Test
    void aNameKeepsItsFirstAndLastLetter() {
        assertEquals("홍*동", MaskType.NAME.apply("홍길동"));
        assertEquals("홍*", MaskType.NAME.apply("홍길"));
        assertEquals("남**수", MaskType.NAME.apply("남궁민수"));
        assertEquals("J*** ****h", MaskType.NAME.apply("John Smith"));
        assertEquals("*", MaskType.NAME.apply("홍"));
    }

    @Test
    void aPlateHidesItsLastFourDigits() {
        assertEquals("12가****", MaskType.PLATE.apply("12가3456"));
        assertEquals("123가****", MaskType.PLATE.apply("123가4567"));
        assertEquals("서울12가****", MaskType.PLATE.apply("서울12가3456"));
    }

    @Test
    void aCardKeepsItsFirstAndLastFourDigits() {
        assertEquals("1234-****-****-3456", MaskType.CARD.apply("1234-5678-9012-3456"));
    }

    @Test
    void aResidentNumberKeepsTheBirthDateAndTheNextDigit() {
        assertEquals("900101-1******", MaskType.RRN.apply("900101-1234567"));
    }
}
