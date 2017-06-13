package slowCheck;

/**
 * @author peter
 */
public class GenChar {
  public static Generator<Character> range(char min, char max) {
    return GenNumber.integers(min, max).map(i -> (char)i.intValue()).noShrink();
  }

  public static Generator<Character> asciiPrintable() {
    return range((char)32, (char)126);
  }

  public static Generator<Character> asciiUppercase() {
    return range('A', 'Z');
  }

  public static Generator<Character> asciiLowercase() {
    return range('a', 'z');
  }

  public static Generator<Character> asciiLetter() {
    return Generator.from(new Frequency<Character>()
      .withAlternative(9, asciiLowercase())
      .withAlternative(1, asciiUppercase()))
      .noShrink();
  }
}
