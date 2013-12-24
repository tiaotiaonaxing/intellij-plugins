package com.intellij.tapestry.tests.mocks;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author <a href="mailto:hugo.palma@logical-software.com">Hugo Palma</a>
 */
public class PsiClassTypeMock extends PsiClassType {
  public PsiClassTypeMock() {
    super(LanguageLevel.JDK_1_5);
  }

  private PsiClass _resolve;

    @Override
    @Nullable
    public PsiClass resolve() {
        return _resolve;
    }

    public PsiClassTypeMock setResolve(PsiClass resolve) {
        _resolve = resolve;

        return this;
    }

    @Override
    public String getClassName() {
        return null;
    }

    @Override
    @NotNull
    public PsiType[] getParameters() {
        return EMPTY_ARRAY;
    }

    @Override
    @NotNull
    public ClassResolveResult resolveGenerics() {
        return null;
    }

    @Override
    @NotNull
    public PsiClassType rawType() {
        return null;
    }

    @Override
    @NotNull
    public GlobalSearchScope getResolveScope() {
        return null;
    }

    @Override
    @NotNull
    public LanguageLevel getLanguageLevel() {
        return null;
    }

    @NotNull
    @Override
    public PsiClassType setLanguageLevel(@NotNull final LanguageLevel languageLevel) {
        return this;
    }

    @NotNull
    @Override
    public String getPresentableText() {
        return "?";
    }

    @NotNull
    @Override
    public String getCanonicalText() {
        return "?";
    }

    @NotNull
    @Override
    public String getInternalCanonicalText() {
        return "?";
    }

    @Override
    public boolean isValid() {
        return false;
    }

    @Override
    public boolean equalsToText(@NotNull @NonNls String text) {
        return false;
    }
}
